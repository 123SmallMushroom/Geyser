/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.erosion;

import com.github.steveice10.mc.protocol.data.game.level.block.value.PistonValueType;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.protocol.bedrock.data.SoundEvent;
import com.nukkitx.protocol.bedrock.packet.LevelSoundEventPacket;
import io.netty.channel.Channel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import org.geysermc.erosion.packet.ErosionPacketHandler;
import org.geysermc.erosion.packet.ErosionPacketSender;
import org.geysermc.erosion.packet.backendbound.BackendboundInitializePacket;
import org.geysermc.erosion.packet.backendbound.BackendboundPacket;
import org.geysermc.erosion.packet.geyserbound.*;
import org.geysermc.geyser.level.block.BlockStateValues;
import org.geysermc.geyser.level.physics.Direction;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.PistonCache;
import org.geysermc.geyser.translator.level.block.entity.PistonBlockEntity;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class GeyserboundPacketHandlerImpl implements GeyserboundPacketHandler {
    private final GeyserSession session;
    private final ErosionPacketSender<BackendboundPacket> packetSender;
    @Getter
    private final Int2ObjectMap<IntConsumer> pendingTransactions = new Int2ObjectOpenHashMap<>();
    @Getter
    private final Int2ObjectMap<Consumer<int[]>> pendingBatchTransactions = new Int2ObjectOpenHashMap<>();

    public GeyserboundPacketHandlerImpl(GeyserSession session, ErosionPacketSender<BackendboundPacket> packetSender) {
        this.session = session;
        this.packetSender = packetSender;
    }

    @Override
    public void handleBatchBlockId(GeyserboundBatchBlockIdPacket packet) {
        var batchConsumer = pendingBatchTransactions.remove(packet.getId());
        if (batchConsumer != null) {
            batchConsumer.accept(packet.getBlocks());
        }
    }

    @Override
    public void handleBlockData(GeyserboundBlockDataPacket packet) {
        IntConsumer consumer = pendingTransactions.remove(packet.getId());
        if (consumer != null) {
            consumer.accept(BlockRegistries.JAVA_IDENTIFIERS.getOrDefault(packet.getBlockData(), 0));
        }
    }

    @Override
    public void handleBlockId(GeyserboundBlockIdPacket packet) {
        IntConsumer consumer = pendingTransactions.remove(packet.getId());
        if (consumer != null) {
            consumer.accept(packet.getBlockId());
        }
    }

    @Override
    public void handleBlockLookupFail(GeyserboundBlockLookupFailPacket packet) {
        IntConsumer consumer = pendingTransactions.remove(packet.getId());
        if (consumer != null) {
            consumer.accept(0);
        } else {
            var batchConsumer = pendingBatchTransactions.remove(packet.getId());
            if (batchConsumer != null) {
                batchConsumer.accept(IntArrays.EMPTY_ARRAY);
            }
        }
    }

    @Override
    public void handleBlockPlace(GeyserboundBlockPlacePacket packet) {
        LevelSoundEventPacket placeBlockSoundPacket = new LevelSoundEventPacket();
        placeBlockSoundPacket.setSound(SoundEvent.PLACE);
        placeBlockSoundPacket.setPosition(packet.getPos().toFloat());
        placeBlockSoundPacket.setBabySound(false);
        placeBlockSoundPacket.setExtraData(session.getBlockMappings().getBedrockBlockId(packet.getBlockId()));
        placeBlockSoundPacket.setIdentifier(":");
        session.sendUpstreamPacket(placeBlockSoundPacket);
        session.setLastBlockPlacePosition(null);
        session.setLastBlockPlacedId(null);
    }

    @Override
    public void handlePistonEvent(GeyserboundPistonEventPacket packet) {
        Direction orientation = BlockStateValues.getPistonOrientation(packet.getBlockId());
        Vector3i position = packet.getPos();
        boolean isExtend = packet.isExtend();

        var stream = packet.getAttachedBlocks()
                .object2IntEntrySet()
                .stream()
                .filter(entry -> BlockStateValues.canPistonMoveBlock(entry.getIntValue(), isExtend));
        Object2IntMap<Vector3i> attachedBlocks = new Object2IntArrayMap<>();
        stream.forEach(entry -> attachedBlocks.put(entry.getKey(), entry.getIntValue()));

        session.executeInEventLoop(() -> {
            PistonCache pistonCache = session.getPistonCache();
            PistonBlockEntity blockEntity = pistonCache.getPistons().computeIfAbsent(position, pos ->
                    new PistonBlockEntity(session, position, orientation, packet.isSticky(), !isExtend));
            blockEntity.setAction(isExtend ? PistonValueType.PUSHING : PistonValueType.PULLING, attachedBlocks);
        });
    }

    @Override
    public void onConnect() {
        sendPacket(new BackendboundInitializePacket(session.getPlayerEntity().getUuid()));
    }

    public void sendPacket(BackendboundPacket packet) {
        this.packetSender.sendPacket(packet);
    }

    public void close() {
        this.packetSender.close();
    }

    @Override
    public ErosionPacketHandler setChannel(Channel channel) {
        this.packetSender.setChannel(channel);
        return this;
    }
}
