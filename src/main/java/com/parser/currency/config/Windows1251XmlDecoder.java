package com.parser.currency.config;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

public class Windows1251XmlDecoder implements HttpMessageConverter<Object> {

    private final HttpMessageConverter<Object> delegate;

    public Windows1251XmlDecoder(HttpMessageConverter<Object> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return delegate.canRead(clazz, mediaType);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return delegate.canWrite(clazz, mediaType);
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return delegate.getSupportedMediaTypes();
    }

    @Override
    public Object read(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        MediaType mediaType = new MediaType(MediaType.APPLICATION_XML, Charset.forName("windows-1251"));
        inputMessage.getHeaders().setContentType(mediaType);
        return delegate.read(clazz, inputMessage);
    }

    @Override
    public void write(Object o, MediaType contentType, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        delegate.write(o, contentType, outputMessage);
    }
}