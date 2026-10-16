package com.luminiflux.lmep.preview;

import com.luminiflux.lmep.LmepConfig;
import com.luminiflux.lmep.util.MtrRailAccess;
import com.luminiflux.lmep.util.RailMathHelper;
import mtr.block.BlockNode;
import mtr.client.ClientData;
import mtr.data.Rail;
import mtr.data.RailAngle;
import mtr.data.RailType;
import mtr.data.TransportMode;
import mtr.item.ItemNodeModifierBase;
import mtr.item.ItemRailModifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Recomputes the rail preview once per client tick.
 *
 * <p>The whole point of this class is to answer, every frame, the question
 * "what rail would MTR build if I right-clicked right now?" &mdash; and to answer it
 * with MTR's own maths rather than an approximation. MTR3 splits that decision across
 * two methods, both of which are mirrored here statement for statement:</p>
 *
 * <ol>
 *   <li>{@code mtr.item.ItemNodeModifierBase#onEndClick} &mdash; validates the click
 *       ({@code clickCondition}), checks that both ends are nodes with the same
 *       transport mode, and derives {@code facingStart} / {@code facingEnd} from the
 *       node angles plus the bearing between the two nodes.</li>
 *   <li>{@code mtr.item.ItemRailModifier#onConnect} &mdash; resolves the rail type
 *       actually stored (continuous-movement modes silently become
 *       {@code CABLE_CAR} / {@code CABLE_CAR_STATION}), then builds two {@link Rail}s
 *       and accepts the connection only when {@code isValid()}, {@code goodRadius()}
 *       and the cable-car constraints all hold.</li>
 * </ol>
 *
 * <p>Mirroring matters for correctness, not just fidelity: the cable-car branch swaps
 * the rail type, and {@code RailType#railSlopeStyle} feeds the vertical sag computed in
 * {@code Rail#getPositionY}. Using the item's own rail type there would report a wrong
 * length and draw a wrong curve for boats and cable cars.</p>
 */
public final class TrackPreviewManager {

    /** The single per-frame snapshot; the renderer and HUD read only this. */
    public static final PreviewState STATE = new PreviewState();

    /** The cumulative line length is stable, so it is recomputed at most once a second. */
    private static final long CUMULATIVE_RECOMPUTE_INTERVAL_TICKS = 20L;

    private static final Walk EMPTY_WALK = new Walk(0.0D, 0);

    private static BlockPos cachedWalkAnchor;
    private static boolean walkCacheValid;
    private static long walkCachedAtTick;
    private static double walkCachedLength;
    private static int walkCachedSegments;

    private static long tickCounter;

    private TrackPreviewManager() {
    }

    /** Called from {@code TickEvent.ClientTickEvent} on the main thread. */
    public static void clientTick() {
        final Minecraft minecraft = Minecraft.getInstance();
        final PreviewState state = STATE;

        state.active = false;
        state.holdingConnector = false;
        state.ghostRail = null;

        if (minecraft.player == null || minecraft.level == null || !LmepConfig.enabled()) {
            return;
        }

        // --- 1. A rail connector must be in hand. ---------------------------------
        // Set before anything else: the HUD keys off this flag, so it has to be true even
        // when no anchor has been picked yet or the crosshair is not on a node.
        final LocalPlayer player = minecraft.player;
        state.holdingConnector = connectorInfoOf(player.getMainHandItem()) != null
                || connectorInfoOf(player.getOffhandItem()) != null;
        if (!state.holdingConnector) {
            return;
        }

        // The anchor is written into the item stack's NBT by the server; vanilla then syncs
        // the player's inventory back, which is the same mechanism MTR's own "selected
        // block" tooltip relies on. Until one has been picked there is nothing to preview.
        final ConnectorHand hand = findConnector(player);
        if (hand == null) {
            return;
        }

        final BlockPos anchor = hand.anchor;
        final BlockState anchorState = minecraft.level.getBlockState(anchor);
        if (!(anchorState.getBlock() instanceof BlockNode)) {
            return; // The anchor was mined or replaced.
        }
        final BlockNode anchorNode = (BlockNode) anchorState.getBlock();

        // --- 2. The crosshair must point at a node MTR would accept. ---------------
        final HitResult hitResult = minecraft.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        final BlockPos cursor = ((BlockHitResult) hitResult).getBlockPos();
        if (cursor.equals(anchor)) {
            return;
        }
        final BlockState cursorState = minecraft.level.getBlockState(cursor);
        if (!(cursorState.getBlock() instanceof BlockNode)) {
            return;
        }
        final BlockNode cursorNode = (BlockNode) cursorState.getBlock();
        if (!clickConditionPasses(hand.item, cursorNode)) {
            return;
        }

        // --- 3. Mirror ItemNodeModifierBase#onEndClick's angle maths. --------------
        final float cursorAngle = BlockNode.getAngle(cursorState);
        final float anchorAngle = BlockNode.getAngle(anchorState);
        final float bearing = (float) Math.toDegrees(Math.atan2(
                anchor.getZ() - cursor.getZ(),
                anchor.getX() - cursor.getX()));

        final RailAngle facingStart = RailAngle.fromAngle(
                cursorAngle + (RailAngle.similarFacing(bearing, cursorAngle) ? 0.0F : 180.0F));
        final RailAngle facingEnd = RailAngle.fromAngle(
                anchorAngle + (RailAngle.similarFacing(bearing, anchorAngle) ? 180.0F : 0.0F));

        final TransportMode transportMode = cursorNode.transportMode;
        final boolean transportMatches = transportMode == anchorNode.transportMode;

        // --- 4. Mirror ItemRailModifier#onConnect's rail type resolution. ---------
        final RailType requestedRailType = hand.info.railType;
        final RailType actualRailType;
        final boolean modeConstraintsMet;

        if (transportMode.continuousMovement) {
            final Block cursorBlock = cursorState.getBlock();
            final Block anchorBlock = anchorState.getBlock();
            if (cursorBlock instanceof BlockNode.BlockContinuousMovementNode
                    && anchorBlock instanceof BlockNode.BlockContinuousMovementNode) {
                final boolean cursorIsStation =
                        ((BlockNode.BlockContinuousMovementNode) cursorBlock).isStation;
                final boolean anchorIsStation =
                        ((BlockNode.BlockContinuousMovementNode) anchorBlock).isStation;
                if (cursorIsStation && anchorIsStation) {
                    modeConstraintsMet = true;
                    actualRailType = requestedRailType.hasSavedRail
                            ? requestedRailType : RailType.CABLE_CAR_STATION;
                } else {
                    final int dx = anchor.getX() - cursor.getX();
                    final int dz = anchor.getZ() - cursor.getZ();
                    modeConstraintsMet = !requestedRailType.hasSavedRail
                            && facingStart.isParallel(facingEnd)
                            && (((facingStart == RailAngle.N || facingStart == RailAngle.S) && dx == 0)
                            || ((facingStart == RailAngle.E || facingStart == RailAngle.W) && dz == 0)
                            || ((facingStart == RailAngle.NE || facingStart == RailAngle.SW) && dx == -dz)
                            || ((facingStart == RailAngle.SE || facingStart == RailAngle.NW) && dx == dz));
                    actualRailType = RailType.CABLE_CAR;
                }
            } else {
                modeConstraintsMet = false;
                actualRailType = requestedRailType;
            }
        } else {
            modeConstraintsMet = true;
            actualRailType = requestedRailType;
        }

        // --- 5. Build the two rails exactly as onConnect would. -------------------
        final Rail rail = new Rail(cursor, facingStart, anchor, facingEnd,
                hand.info.oneWay ? RailType.NONE : actualRailType, transportMode);
        final Rail reverseRail = new Rail(anchor, facingEnd, cursor, facingStart,
                actualRailType, transportMode);

        final boolean goodRadius = rail.goodRadius() && reverseRail.goodRadius();
        final boolean geometryValid = rail.isValid() && reverseRail.isValid();
        final boolean valid = transportMatches && modeConstraintsMet && goodRadius && geometryValid;

        // --- 6. Publish. ----------------------------------------------------------
        state.active = true;
        state.valid = valid;
        state.invalidReason = invalidReason(transportMatches, modeConstraintsMet, goodRadius);
        state.anchor = anchor;
        state.cursor = cursor;
        state.ghostRail = rail;
        state.facingStart = facingStart;
        state.facingEnd = facingEnd;
        state.railType = actualRailType;
        state.oneWay = hand.info.oneWay;
        state.transportMode = transportMode;

        state.lengthParam = rail.getLength();
        state.length3D = RailMathHelper.trueLength3D(rail);
        state.lengthXZ = RailMathHelper.lengthXZ(rail);
        state.deltaX = cursor.getX() - anchor.getX();
        state.deltaY = cursor.getY() - anchor.getY();
        state.deltaZ = cursor.getZ() - anchor.getZ();
        state.slopePercent = state.lengthXZ > 1.0E-6D ? state.deltaY / state.lengthXZ * 100.0D : 0.0D;

        final double[] radii = MtrRailAccess.radiiOf(rail);
        state.radiusStart = radii[0];
        state.radiusEnd = radii[1];

        updateCumulativeLength(state, anchor);
    }

    /**
     * MTR's own precedence for the failure message
     * ({@code ItemRailModifier#onConnect}, tail of the method).
     */
    private static String invalidReason(boolean transportMatches, boolean modeConstraintsMet, boolean goodRadius) {
        if (!transportMatches) {
            return "lmep.reason.transport_mismatch";
        }
        if (!modeConstraintsMet) {
            return "lmep.reason.cable_car_invalid";
        }
        if (!goodRadius) {
            return "lmep.reason.radius_too_small";
        }
        return "lmep.reason.invalid_orientation";
    }

    /** Mirror of {@code ItemNodeModifierBase#clickCondition}. */
    private static boolean clickConditionPasses(ItemRailModifier item, BlockNode node) {
        if (node.transportMode == TransportMode.AIRPLANE) {
            return item.forAirplaneNode;
        }
        if (node.transportMode.continuousMovement) {
            return item.forContinuousMovementNode;
        }
        return item.forNonContinuousMovementNode;
    }

    private static ConnectorHand findConnector(LocalPlayer player) {
        final ConnectorHand mainHand = readHand(player.getMainHandItem());
        return mainHand != null ? mainHand : readHand(player.getOffhandItem());
    }

    private static ConnectorHand readHand(ItemStack stack) {
        final MtrRailAccess.RailItemInfo info = connectorInfoOf(stack);
        if (info == null) {
            return null;
        }
        if (!stack.hasTag() || !stack.getTag().contains(ItemNodeModifierBase.TAG_POS)) {
            return null; // No anchor selected yet.
        }
        return new ConnectorHand((ItemRailModifier) stack.getItem(), info,
                BlockPos.of(stack.getTag().getLong(ItemNodeModifierBase.TAG_POS)));
    }

    /**
     * @return the connector metadata for a stack, or {@code null} when the stack is not a
     *         usable rail connector. The rail remover is itself an {@link ItemRailModifier}
     *         with a {@code null} rail type, which is how the two are told apart.
     */
    private static MtrRailAccess.RailItemInfo connectorInfoOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemRailModifier)) {
            return null;
        }
        final MtrRailAccess.RailItemInfo info = MtrRailAccess.infoOf((ItemRailModifier) stack.getItem());
        return info == null || info.railType == null ? null : info;
    }

    // ------------------------------------------------------------ cumulative length

    /**
     * Adds up the longest existing line reachable from the anchor, so a builder can
     * size a train against the line rather than against a single segment.
     */
    private static void updateCumulativeLength(PreviewState state, BlockPos anchor) {
        if (!LmepConfig.showCumulativeLength()) {
            state.cumulativeLength = state.length3D;
            state.cumulativeSegments = 1;
            return;
        }

        tickCounter++;
        if (!walkCacheValid
                || !anchor.equals(cachedWalkAnchor)
                || tickCounter - walkCachedAtTick >= CUMULATIVE_RECOMPUTE_INTERVAL_TICKS) {
            final Walk walk = walkLongestPath(anchor, ClientData.RAILS,
                    LmepConfig.maxWalkDepth(), LmepConfig.maxWalkVisits());
            cachedWalkAnchor = anchor;
            walkCachedAtTick = tickCounter;
            walkCachedLength = walk.length;
            walkCachedSegments = walk.segments;
            walkCacheValid = true;
        }

        state.cumulativeLength = walkCachedLength + state.length3D;
        state.cumulativeSegments = walkCachedSegments + 1;
    }

    /**
     * Longest simple path from {@code start} over MTR's bidirectional rail map.
     *
     * <p>The search is bounded twice over: by depth, and by a global visit budget. The
     * budget is what keeps a large network from turning this into an exponential walk
     * every second &mdash; the result is a very good estimate rather than an exact
     * longest path, which is all a length readout needs.</p>
     */
    private static Walk walkLongestPath(BlockPos start,
                                        Map<BlockPos, Map<BlockPos, Rail>> rails,
                                        int maxDepth, int maxVisits) {
        if (rails == null || rails.isEmpty()) {
            return EMPTY_WALK;
        }
        final Set<Long> visited = new HashSet<>();
        visited.add(start.asLong());
        return walkDepthFirst(start, rails, visited, maxDepth, new int[]{maxVisits});
    }

    private static Walk walkDepthFirst(BlockPos position,
                                       Map<BlockPos, Map<BlockPos, Rail>> rails,
                                       Set<Long> visited, int remainingDepth, int[] visitBudget) {
        if (remainingDepth <= 0 || visitBudget[0] <= 0) {
            return EMPTY_WALK;
        }
        visitBudget[0]--;

        double bestLength = 0.0D;
        int bestSegments = 0;

        final Map<BlockPos, Rail> neighbours = rails.get(position);
        if (neighbours != null) {
            for (final Map.Entry<BlockPos, Rail> entry : neighbours.entrySet()) {
                if (visitBudget[0] <= 0) {
                    break;
                }
                final BlockPos next = entry.getKey();
                final Rail rail = entry.getValue();
                if (rail == null || !visited.add(next.asLong())) {
                    continue;
                }
                final Walk sub = walkDepthFirst(next, rails, visited, remainingDepth - 1, visitBudget);
                visited.remove(next.asLong());

                final double candidate = RailMathHelper.approxLength3D(rail) + sub.length;
                if (candidate > bestLength) {
                    bestLength = candidate;
                    bestSegments = 1 + sub.segments;
                }
            }
        }
        return new Walk(bestLength, bestSegments);
    }

    private static final class Walk {
        final double length;
        final int segments;

        Walk(double length, int segments) {
            this.length = length;
            this.segments = segments;
        }
    }

    private static final class ConnectorHand {
        final ItemRailModifier item;
        final MtrRailAccess.RailItemInfo info;
        final BlockPos anchor;

        ConnectorHand(ItemRailModifier item, MtrRailAccess.RailItemInfo info, BlockPos anchor) {
            this.item = item;
            this.info = info;
            this.anchor = anchor;
        }
    }
}
