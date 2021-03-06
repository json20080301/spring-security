/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.security.config.annotation.web.socket;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.messaging.access.expression.MessageExpressionVoter;
import org.springframework.security.messaging.access.intercept.ChannelSecurityInterceptor;
import org.springframework.security.messaging.access.intercept.MessageSecurityMetadataSource;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.security.messaging.web.csrf.CsrfChannelInterceptor;
import org.springframework.security.messaging.web.socket.server.CsrfTokenHandshakeInterceptor;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.socket.sockjs.SockJsService;
import org.springframework.web.socket.sockjs.support.SockJsHttpRequestHandler;
import org.springframework.web.socket.sockjs.transport.TransportHandlingSockJsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Allows configuring WebSocket Authorization.
 *
 * <p>For example:</p>
 *
 * <pre>
 * @Configuration
 * public class WebSocketSecurityConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {
 *
 *   @Override
 *   protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
 *     messages
 *       .simpDestMatchers("/user/queue/errors").permitAll()
 *       .simpDestMatchers("/admin/**").hasRole("ADMIN")
 *       .anyMessage().authenticated();
 *   }
 * }
 * </pre>
 *
 *
 * @since 4.0
 * @author Rob Winch
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public abstract class AbstractSecurityWebSocketMessageBrokerConfigurer extends AbstractWebSocketMessageBrokerConfigurer
        implements SmartInitializingSingleton {
    private final WebSocketMessageSecurityMetadataSourceRegistry inboundRegistry = new WebSocketMessageSecurityMetadataSourceRegistry();

    private ApplicationContext context;

    public void registerStompEndpoints(StompEndpointRegistry registry) {}

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(new AuthenticationPrincipalArgumentResolver());
    }

    @Override
    public final void configureClientInboundChannel(ChannelRegistration registration) {
        ChannelSecurityInterceptor inboundChannelSecurity = inboundChannelSecurity();
        registration.setInterceptors(securityContextChannelInterceptor());
        if(sameOriginEnforced()) {
            registration.setInterceptors(csrfChannelInterceptor());
        }
        if(inboundRegistry.containsMapping()) {
            registration.setInterceptors(inboundChannelSecurity);
        }
        customizeClientInboundChannel(registration);
    }

    /**
     * <p>
     * Determines if a CSRF token is required for connecting. This protects against remote sites from connecting to the
     * application and being able to read/write data over the connection. The default is true.
     * </p>
     * <p>
     * Subclasses can override this method to disable CSRF protection
     * </p>
     *
     * @return true if a CSRF is required for connecting, else false
     */
    protected boolean sameOriginEnforced() {
        return true;
    }

    /**
     * Allows subclasses to customize the configuration of the {@link ChannelRegistration}.
     *
     * @param registration the {@link ChannelRegistration} to customize
     */
    protected void customizeClientInboundChannel(ChannelRegistration registration) {
    }

    @Bean
    public CsrfChannelInterceptor csrfChannelInterceptor() {
        return new CsrfChannelInterceptor();
    }

    @Bean
    public ChannelSecurityInterceptor inboundChannelSecurity() {
        ChannelSecurityInterceptor channelSecurityInterceptor = new ChannelSecurityInterceptor(inboundMessageSecurityMetadataSource());
        List<AccessDecisionVoter<? extends Object>> voters = new ArrayList<AccessDecisionVoter<? extends Object>>();
        voters.add(new MessageExpressionVoter<Object>());
        AffirmativeBased manager = new AffirmativeBased(voters);
        channelSecurityInterceptor.setAccessDecisionManager(manager);
        return channelSecurityInterceptor;
    }

    @Bean
    public SecurityContextChannelInterceptor securityContextChannelInterceptor() {
        return new SecurityContextChannelInterceptor();
    }

    @Bean
    public MessageSecurityMetadataSource inboundMessageSecurityMetadataSource() {
        configureInbound(inboundRegistry);
        return inboundRegistry.createMetadataSource();
    }

    /**
     *
     * @param messages
     */
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {}

    private class WebSocketMessageSecurityMetadataSourceRegistry extends MessageSecurityMetadataSourceRegistry {
        @Override
        public MessageSecurityMetadataSource createMetadataSource() {
            return super.createMetadataSource();
        }

        @Override
        protected boolean containsMapping() {
            return super.containsMapping();
        }
    }

    @Autowired
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }

    public void afterSingletonsInstantiated() {
        if(!sameOriginEnforced()) {
            return;
        }

        String beanName = "stompWebSocketHandlerMapping";
        SimpleUrlHandlerMapping mapping = context.getBean(beanName, SimpleUrlHandlerMapping.class);
        Map<String, Object> mappings = mapping.getHandlerMap();
        for(Object object : mappings.values()) {
            if(object instanceof SockJsHttpRequestHandler) {
                SockJsHttpRequestHandler sockjsHandler = (SockJsHttpRequestHandler) object;
                SockJsService sockJsService = sockjsHandler.getSockJsService();
                if(!(sockJsService instanceof TransportHandlingSockJsService)) {
                    throw new IllegalStateException("sockJsService must be instance of TransportHandlingSockJsService got " + sockJsService);
                }

                TransportHandlingSockJsService transportHandlingSockJsService = (TransportHandlingSockJsService) sockJsService;
                List<HandshakeInterceptor> handshakeInterceptors = transportHandlingSockJsService.getHandshakeInterceptors();
                List<HandshakeInterceptor> interceptorsToSet = new ArrayList<HandshakeInterceptor>(handshakeInterceptors.size() + 1);
                interceptorsToSet.add(new CsrfTokenHandshakeInterceptor());
                interceptorsToSet.addAll(handshakeInterceptors);

                transportHandlingSockJsService.setHandshakeInterceptors(interceptorsToSet);
            }
            else if(object instanceof WebSocketHttpRequestHandler) {
                WebSocketHttpRequestHandler handler = (WebSocketHttpRequestHandler) object;
                List<HandshakeInterceptor> handshakeInterceptors = handler.getHandshakeInterceptors();
                List<HandshakeInterceptor> interceptorsToSet = new ArrayList<HandshakeInterceptor>(handshakeInterceptors.size() + 1);
                interceptorsToSet.add(new CsrfTokenHandshakeInterceptor());
                interceptorsToSet.addAll(handshakeInterceptors);

                handler.setHandshakeInterceptors(interceptorsToSet);
            } else {
                throw new IllegalStateException("Bean " + beanName + " is expected to contain mappings to either a SockJsHttpRequestHandler or a WebSocketHttpRequestHandler but got " + object);
            }
        }
    }
}