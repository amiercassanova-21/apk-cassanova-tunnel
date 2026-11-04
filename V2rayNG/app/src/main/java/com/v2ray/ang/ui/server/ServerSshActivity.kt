package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.v2ray.ang.R
import com.v2ray.ang.core.ssh.SshConfigError
import com.v2ray.ang.core.ssh.SshMode
import com.v2ray.ang.core.ssh.SshSettings
import com.v2ray.ang.core.ssh.SshTunnelManager
import com.v2ray.ang.core.ssh.validate
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField

class ServerSshActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.SSH

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = serverConfigType
        }

        ServerEditorScaffold(
            title = stringResource(R.string.menu_item_import_config_manually_ssh),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            SshFields(uiState)
        }
    }

    @Composable
    private fun SshFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_ssh_username),
            state.username,
            { state.username = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_ssh_password),
            state.password,
            { state.password = it },
            isError = state.isPasswordError
        )

        val modeLabels = linkedMapOf(
            SshMode.DIRECT to stringResource(R.string.ssh_mode_direct),
            SshMode.PAYLOAD to stringResource(R.string.ssh_mode_payload),
            SshMode.SSL to stringResource(R.string.ssh_mode_ssl),
            SshMode.SSL_PAYLOAD to stringResource(R.string.ssh_mode_ssl_payload),
            SshMode.SLOWDNS to stringResource(R.string.ssh_mode_slowdns),
        )
        val mode = SshMode.from(state.sshMode)
        FormDropdownField(
            stringResource(R.string.server_lab_ssh_mode),
            modeLabels.getValue(mode),
            modeLabels.values.toList(),
            { label -> state.sshMode = modeLabels.entries.first { it.value == label }.key.key }
        )

        if (mode.usesPayload) {
            FormTextField(
                stringResource(R.string.server_lab_ssh_payload),
                state.sshPayload,
                { state.sshPayload = it },
                placeholder = stringResource(R.string.server_lab_ssh_payload_hint),
                maxLines = 8
            )
            FormTextField(
                stringResource(R.string.server_lab_ssh_proxy_host),
                state.sshProxyHost,
                { state.sshProxyHost = it }
            )
            FormTextField(
                stringResource(R.string.server_lab_ssh_proxy_port),
                state.sshProxyPort,
                { state.sshProxyPort = it },
                keyboardType = KeyboardType.Number
            )
        }

        if (mode.usesTls) {
            FormTextField(
                stringResource(R.string.server_lab_ssh_sni),
                state.sni,
                { state.sni = it }
            )
        }

        if (mode == SshMode.SLOWDNS) {
            FormTextField(
                stringResource(R.string.server_lab_dns_resolver),
                state.dnsResolver,
                { state.dnsResolver = it },
                placeholder = stringResource(R.string.server_lab_dns_resolver_hint)
            )
            FormTextField(
                stringResource(R.string.server_lab_dns_nameserver),
                state.dnsNameserver,
                { state.dnsNameserver = it }
            )
            FormTextField(
                stringResource(R.string.server_lab_dns_public_key),
                state.dnsPublicKey,
                { state.dnsPublicKey = it }
            )
            val recordTypes = listOf("TXT", "NULL")
            FormDropdownField(
                stringResource(R.string.server_lab_dns_record_type),
                state.dnsRecordType.ifBlank { SshSettings.DNS_TYPE_TXT }.uppercase(),
                recordTypes,
                { state.dnsRecordType = it.lowercase() }
            )
        }

        FormTextField(
            stringResource(R.string.server_lab_ssh_udpgw_port),
            state.sshUdpgwPort,
            { state.sshUdpgwPort = it },
            keyboardType = KeyboardType.Number
        )
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        val error = SshTunnelManager.toSettings(config).validate() ?: return true
        toast(
            when (error) {
                SshConfigError.MISSING_CREDENTIALS -> R.string.toast_ssh_missing_credentials
                SshConfigError.MISSING_PAYLOAD -> R.string.toast_ssh_missing_payload
                SshConfigError.MISSING_SLOWDNS -> R.string.toast_ssh_missing_slowdns
            }
        )
        return false
    }
}
