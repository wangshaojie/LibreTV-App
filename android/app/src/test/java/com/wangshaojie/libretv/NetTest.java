package com.wangshaojie.libretv;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 老电视只有链路本地 IPv6、没有 IPv6 路由，底层不会在 ENETUNREACH 后回退到 IPv4。
 * Net 在确认 IPv6 不可达时改用 IPv4 重试，这里验证地址挑选与回退判定。
 *
 * 全部用字面量地址构造，不做 DNS 查询，测试不依赖网络。
 */
public class NetTest {

    private static InetAddress v4(int a,int b,int c,int d)throws Exception{
        return InetAddress.getByAddress(new byte[]{(byte)a,(byte)b,(byte)c,(byte)d});
    }

    private static InetAddress v6(int last)throws Exception{
        byte[] bytes=new byte[16];
        bytes[0]=0x26;bytes[1]=0x06;bytes[2]=0x50;bytes[3]=(byte)0xc0;
        bytes[15]=(byte)last;
        return InetAddress.getByAddress(bytes);
    }

    @Test
    public void picksIpv4WhenBothFamiliesPresent()throws Exception{
        InetAddress[] mixed={v6(1),v4(93,184,216,34)};
        assertEquals("93.184.216.34",Net.firstIpv4(mixed).getHostAddress());
    }

    @Test
    public void picksIpv4EvenWhenItComesSecond()throws Exception{
        InetAddress[] mixed={v6(0x54),v4(93,184,216,34)};
        assertEquals("93.184.216.34",Net.firstIpv4(mixed).getHostAddress());
    }

    @Test
    public void returnsNullWhenOnlyIpv6()throws Exception{
        assertNull(Net.firstIpv4(new InetAddress[]{v6(0x54)}));
    }

    @Test
    public void returnsNullForEmptyResult(){
        assertNull(Net.firstIpv4(new InetAddress[0]));
        assertNull(Net.firstIpv4(null));
    }

    @Test
    public void ipv4AddressIsRecognisedAsInet4()throws Exception{
        assertTrue(v4(93,184,216,34) instanceof Inet4Address);
    }

    @Test
    public void ipv6AddressIsRecognisedAsInet6()throws Exception{
        assertTrue(v6(0x54) instanceof Inet6Address);
    }

    @Test
    public void detectsUnreachableIpv6Failure(){
        ConnectException error=new ConnectException("failed to connect to raw.githubusercontent.com/2606:50c0:8001::154 (port 443) after 12000ms: connect failed: ENETUNREACH (Network is unreachable)");
        assertTrue(Net.unreachable(error));
    }

    @Test
    public void detectsUnreachableWrappedInCause(){
        IOException error=new IOException("请求失败",new ConnectException("connect failed: ENETUNREACH (Network is unreachable)"));
        assertTrue(Net.unreachable(error));
    }

    @Test
    public void ignoresUnrelatedFailures(){
        assertFalse(Net.unreachable(new ConnectException("Connection timed out")));
        assertFalse(Net.unreachable(new IOException("HTTP 404")));
        assertFalse(Net.unreachable(new IOException("清单超过 12 MB")));
    }

    @Test
    public void ignoresFailureWithoutMessage(){
        assertFalse(Net.unreachable(new ConnectException()));
    }
}
