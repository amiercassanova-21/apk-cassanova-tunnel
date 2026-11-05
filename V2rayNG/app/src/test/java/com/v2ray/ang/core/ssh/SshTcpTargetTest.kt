package com.v2ray.ang.core.ssh

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SshTcpTargetTest {

    private fun ssh(block: ProfileItem.() -> Unit) = ProfileItem.create(EConfigType.SSH).apply {
        server = "ssh.example.com"
        serverPort = "443"
        block()
    }

    @Test
    fun slowDnsHasNoTcpTarget() {
        assertNull(sshTcpTarget(ssh { sshMode = "slowdns" }))
    }

    @Test
    fun payloadProxyWinsOverServer() {
        assertEquals("bug.example.com" to 80, sshTcpTarget(ssh {
            sshMode = "payload"; sshProxyHost = "bug.example.com"; sshProxyPort = "80"
        }))
    }

    @Test
    fun directUsesServerAndDefaultPort() {
        assertEquals("ssh.example.com" to 443, sshTcpTarget(ssh { sshMode = "direct" }))
        assertEquals("ssh.example.com" to 22, sshTcpTarget(ssh { serverPort = "" }))
        assertNull(sshTcpTarget(ssh { server = " " }))
    }
}
