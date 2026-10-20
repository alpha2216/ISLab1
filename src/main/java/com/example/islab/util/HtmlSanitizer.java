package com.example.islab.util;

import org.springframework.web.util.HtmlUtils;

public final class HtmlSanitizer {
	private HtmlSanitizer() {}

    public static String escape(String s) {
        return s == null ? null : HtmlUtils.htmlEscape(s);
    }
}
