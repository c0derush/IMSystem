package com.im.gateway.config;

import com.im.server.protocol.handler.PacketHandler;
import com.im.server.protocol.handler.PacketRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RouterConfig {

    @Bean
    public PacketRouter packetRouter(List<PacketHandler> handlers) {
        PacketRouter router = new PacketRouter();
        for (PacketHandler handler : handlers) {
            router.register(handler);
        }
        return router;
    }
}
