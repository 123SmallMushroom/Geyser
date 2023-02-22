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

import io.netty.channel.Channel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import org.geysermc.erosion.packet.ErosionPacketHandler;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockDataPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockIdPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundPacketHandler;
import org.geysermc.geyser.registry.BlockRegistries;

import java.util.function.IntConsumer;

public class GeyserboundPacketHandlerImpl implements GeyserboundPacketHandler {
    @Getter
    private final Int2ObjectMap<IntConsumer> pendingTransactions = new Int2ObjectOpenHashMap<>();
    @Getter
    private Channel channel;

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
    public ErosionPacketHandler setChannel(Channel channel) {
        this.channel = channel;
        return this;
    }
}
