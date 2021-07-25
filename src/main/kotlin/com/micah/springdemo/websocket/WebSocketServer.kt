package com.micah.springdemo.websocket

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.stream.ChunkedWriteHandler
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

/**
 * @author micah
 */
@Service
class WebSocketServer {
    private val workerGroup: EventLoopGroup = NioEventLoopGroup()
    private val bossGroup: EventLoopGroup = NioEventLoopGroup()
    fun run() {
        val boot = ServerBootstrap()
        boot.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<Channel>() {
                @kotlin.Throws(Exception::class)
                override fun initChannel(ch: Channel) {
                    val pipeline = ch.pipeline()
                    pipeline.addLast("http-codec", HttpServerCodec())
                    pipeline.addLast("aggregator", HttpObjectAggregator(65536))
                    pipeline.addLast("http-chunked", ChunkedWriteHandler())
                    pipeline.addLast("handler", WebSocketServerHandler())
                }
            })
        try {
            val ch = boot.bind(2048).sync().channel()
            println("websocket server start at port:2048")
            ch.closeFuture().sync()
        } catch (e: InterruptedException) {
            throw InterruptedException(e.message)
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }

    @PostConstruct
    fun init() {
        val t = Thread {
            try {
                run()
            } catch (e: Exception) {
                throw Exception(e.message)
            }
        }
        t.start()
    }
}