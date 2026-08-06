package com.bobfull.chat.dto;
import java.util.List;
public record ChatMessageSliceResponse(List<ChatMessageResponse> content, Long nextCursor) { }
