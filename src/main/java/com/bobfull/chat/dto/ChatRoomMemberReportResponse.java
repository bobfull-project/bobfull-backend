package com.bobfull.chat.dto;
import com.bobfull.chat.entity.*; import java.time.Instant;
public record ChatRoomMemberReportResponse(Long reportId, Long chatRoomId, Long reporterMemberId, Long reportedMemberId, Long anchorMessageId, ReportReason reason, String detail, ReportStatus status, Instant createdAt) { public static ChatRoomMemberReportResponse from(ChatRoomMemberReport r){return new ChatRoomMemberReportResponse(r.getId(),r.getChatRoomId(),r.getReporterMemberId(),r.getReportedMemberId(),r.getAnchorMessageId(),r.getReason(),r.getDetail(),r.getStatus(),r.getCreatedAt());} }
