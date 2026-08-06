package com.bobfull.chat.controller;
import com.bobfull.chat.dto.*;
import com.bobfull.chat.security.StompPrincipal;
import com.bobfull.chat.service.ChatMessageCommandService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.security.Principal;
@Controller public class ChatMessageController {
    private final ChatMessageCommandService service; private final SimpMessagingTemplate template;
    public ChatMessageController(ChatMessageCommandService service,SimpMessagingTemplate template){this.service=service;this.template=template;}
    @MessageMapping("/chat/rooms/{chatRoomId}/messages")
    public void send(@DestinationVariable Long chatRoomId,@Payload ChatMessageSendRequest request,Principal principal){
        if(!(principal instanceof StompPrincipal stompPrincipal)) throw new com.bobfull.chat.security.StompAuthenticationException(com.bobfull.chat.security.StompAuthenticationException.Reason.MISSING_AUTHORIZATION);
        ChatMessageSentResponse response=service.send(chatRoomId,stompPrincipal.authMember(),request.content());
        template.convertAndSend("/sub/chat/rooms/"+chatRoomId,response);
    }
}
