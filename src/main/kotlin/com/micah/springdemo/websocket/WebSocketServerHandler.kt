package com.micah.springdemo.websocket

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.websocketx.*
import io.netty.util.CharsetUtil

class WebSocketServerHandler : SimpleChannelInboundHandler<Any?>() {
    private var handshaker: WebSocketServerHandshaker? = null

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any?) {
        /**
         * HTTP接入，WebSocket第一次连接使用HTTP连接,用于握手
         */
        if (msg is FullHttpRequest) {
            handleHttpRequest(ctx, msg)
        } else if (msg is WebSocketFrame) {
            handlerWebSocketFrame(ctx, msg)
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
    }

    private fun handleHttpRequest(ctx: ChannelHandlerContext, req: FullHttpRequest) {
        if (!req.decoderResult.isSuccess
            || "websocket" != req.headers()["Upgrade"]
        ) {
            sendHttpResponse(
                ctx, req, DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST
                )
            )
            return
        }
        val wsFactory = WebSocketServerHandshakerFactory(
            "ws://localhost:2048/ws", null, false
        )
        handshaker = wsFactory.newHandshaker(req)
        if (handshaker == null) {
            WebSocketServerHandshakerFactory
                .sendUnsupportedWebSocketVersionResponse(ctx.channel())
        } else {
            handshaker!!.handshake(ctx.channel(), req)
        }
    }

    private fun handlerWebSocketFrame(
        ctx: ChannelHandlerContext,
        frame: WebSocketFrame
    ) {
        /**
         * 判断是否关闭链路的指令
         */
        if (frame is CloseWebSocketFrame) {
            handshaker!!.close(
                ctx.channel(),
                frame.retain() as CloseWebSocketFrame
            )
            return
        }
        /**
         * 判断是否ping消息
         */
        if (frame is PingWebSocketFrame) {
            ctx.channel().write(
                PongWebSocketFrame(frame.content().retain())
            )
            return
        }
        if (frame is BinaryWebSocketFrame) {
            throw UnsupportedOperationException(
                String.format(
                    "%s frame types not supported", frame.javaClass.name
                )
            )
        }
        if (frame is TextWebSocketFrame) {
            // 返回应答消息
            val request = frame.text()
            println("服务端收到：$request")
            ctx.channel().write(TextWebSocketFrame("服务器收到并返回：$request"))
        }
    }

    companion object {
        private fun sendHttpResponse(
            ctx: ChannelHandlerContext,
            req: FullHttpRequest, res: DefaultFullHttpResponse
        ) {
            // 返回应答给客户端
            if (res.status.code() != 200) {
                val buf = Unpooled.copiedBuffer(
                    res.status.toString(),
                    CharsetUtil.UTF_8
                )
                res.content().writeBytes(buf)
                buf.release()
            }
            // 如果是非Keep-Alive，关闭连接
            val f = ctx.channel().writeAndFlush(res)
            if (!isKeepAlive(req) || res.status.code() != 200) {
                f.addListener(ChannelFutureListener.CLOSE)
            }
        }

        private fun isKeepAlive(req: FullHttpRequest): Boolean {
            return false
        }
    }
}