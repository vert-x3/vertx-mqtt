/*
 * Copyright (c) 2026 IoT Invent GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * AI Disclosure: This file was largely AI-generated. The AI-generated
 * portions are made available under CC0-1.0 and not subject to the
 * project's licence. The human contributor has reviewed and verified
 * that the code is correct.
 *
 * SPDX-License-Identifier: Apache-2.0 AND CC0-1.0
 * Assisted-by: Anthropic Claude Opus 5.5 (claude-opus-5-5)
 */

package io.vertx.mqtt.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.util.List;

/**
 * Codecs translating between MQTT byte streams and binary WebSocket frames, shared by client and server.
 */
final class MqttWebSocketCodecs {

  private MqttWebSocketCodecs() {
  }

  static class WebSocketFrameToByteBufDecoder extends MessageToMessageDecoder<WebSocketFrame> {

    @Override
    protected void decode(ChannelHandlerContext chc, WebSocketFrame frame, List<Object> out)
      throws Exception {
      if (frame instanceof BinaryWebSocketFrame || frame instanceof ContinuationWebSocketFrame) {
        // convert the frame to a ByteBuf, a fragmented MQTT packet continues in continuation frames
        ByteBuf bb = frame.content();
        bb.retain();
        out.add(bb);
      } else {
        out.add(frame.retain());
      }
    }
  }

  static class ByteBufToWebSocketFrameEncoder extends MessageToMessageEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext chc, ByteBuf bb, List<Object> out) throws Exception {
      // convert the ByteBuf to a WebSocketFrame
      BinaryWebSocketFrame result = new BinaryWebSocketFrame();
      result.content().writeBytes(bb);
      out.add(result);
    }
  }
}
