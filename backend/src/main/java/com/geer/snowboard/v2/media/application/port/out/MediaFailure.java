package com.geer.snowboard.v2.media.application.port.out;
public final class MediaFailure extends RuntimeException {
    private final String code;private final boolean invalid;
    public MediaFailure(String code,boolean invalid){super(code);this.code=code;this.invalid=invalid;}
    public String code(){return code;}
    public boolean invalid(){return invalid;}
}
