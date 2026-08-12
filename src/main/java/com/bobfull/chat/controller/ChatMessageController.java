package com.bobfull.chat.controller;
import com.bobfull.chat.dto.*;
import com.bobfull.chat.security.StompPrincipal;
import com.bobfull.chat.service.ChatMessageCommandService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;
@Controller public class ChatMessageController {
    private final ChatMessageCommandService service;
    public ChatMessageController(ChatMessageCommandService service){this.service=service;}
    @MessageMapping("/chat/rooms/{chatRoomId}/messages")
    public void send(@DestinationVariable Long chatRoomId,@Payload ChatMessageSendRequest request,Principal principal){
        if(!(principal instanceof StompPrincipal stompPrincipal)) throw new com.bobfull.chat.security.StompAuthenticationException(com.bobfull.chat.security.StompAuthenticationException.Reason.MISSING_AUTHORIZATION);
        service.send(chatRoomId,stompPrincipal.authMember(),request.content());
    }
}
