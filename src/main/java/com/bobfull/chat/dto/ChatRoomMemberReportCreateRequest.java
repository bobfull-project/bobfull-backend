package com.bobfull.chat.dto;
import com.bobfull.chat.entity.ReportReason;
import jakarta.validation.constraints.*;
public record ChatRoomMemberReportCreateRequest(@NotNull ReportReason reason, Long anchorMessageId, @Size(max=500) String detail) { }
