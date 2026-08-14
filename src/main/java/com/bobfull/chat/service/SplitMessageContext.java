package com.bobfull.chat.service;

import com.bobfull.chat.entity.ChatMessage;
import java.util.List;

/** #266에서만 사용하는 same-room/same-sender의 짧은 메시지 결합 입력이다. */
record SplitMessageContext(List<String> fragments, String joinedNormalized) {
    static SplitMessageContext from(List<ChatMessage> messages) {
        List<String> fragments = messages.stream().map(ChatMessage::getContent).toList();
        String joined = fragments.stream().map(SplitMessageContext::normalize).reduce("", String::concat);
        return new SplitMessageContext(fragments, joined);
    }

    boolean containsMultipleMessages() {
        return fragments.size() > 1;
    }

    private static String normalize(String content) {
        String koreanOnly = content.replaceAll("[^가-힣]", "");
        return koreanOnly.replaceAll("씨이+", "씨").replaceAll("시이+(?=발)", "시");
    }
}
