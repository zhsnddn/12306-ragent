package com.ming.agent12306.sso.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SsoUserInfo(
        String userId,
        String username,
        String realName
) {
}
