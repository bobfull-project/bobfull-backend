package com.bobfull.chat.port;
import java.util.Collection;
import java.util.Map;
public interface MemberNameReader { Map<Long, String> readNames(Collection<Long> memberIds); }
