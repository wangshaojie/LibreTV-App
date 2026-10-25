package com.wangshaojie.libretv;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 自建 HTTPS 通道要自己解析 HTTP 响应，这里覆盖两种常见的正文长度：
 * Content-Length 与 chunked 分块。
 */
public class Ipv4ClientTest {

    private static InputStream stream(String text){
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    public void readsBodyWithContentLength() throws Exception {
        Ipv4Client.Response response=Ipv4Client.read(stream(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: 13\r\n\r\n{\"total\":100}"),1024,false);
        assertEquals(200,response.code);
        assertEquals("application/json; charset=utf-8",response.type);
        assertEquals("{\"total\":100}",new String(response.data,StandardCharsets.UTF_8));
        assertNull(response.location);
    }

    @Test
    public void readsChunkedBody() throws Exception {
        Ipv4Client.Response response=Ipv4Client.read(stream(
                "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n"),1024,false);
        assertEquals(200,response.code);
        assertEquals("hello world",new String(response.data,StandardCharsets.UTF_8));
    }

    @Test
    public void reportsRedirectLocation() throws Exception {
        Ipv4Client.Response response=Ipv4Client.read(stream(
                "HTTP/1.1 302 Found\r\nLocation: https://example.com/moved\r\nContent-Length: 0\r\n\r\n"),1024,false);
        assertEquals(302,response.code);
        assertEquals("https://example.com/moved",response.location);
        assertEquals(0,response.data.length);
    }

    @Test
    public void keepsOnlyTheHeadWhenSampling() throws Exception {
        Ipv4Client.Response response=Ipv4Client.read(stream(
                "HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: 100000\r\n\r\n0123456789"),8192,true);
        assertEquals(200,response.code);
        assertEquals(10,response.data.length);
    }

    @Test
    public void stopsAtTheSizeLimit() throws Exception {
        Ipv4Client.Response response=Ipv4Client.read(stream(
                "HTTP/1.1 200 OK\r\nContent-Length: 100000\r\n\r\n0123456789"),4,false);
        assertEquals(4,response.data.length);
    }
}
