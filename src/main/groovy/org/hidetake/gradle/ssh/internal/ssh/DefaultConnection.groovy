package org.hidetake.gradle.ssh.internal.ssh

import com.jcraft.jsch.*
import groovy.util.logging.Slf4j
import org.hidetake.gradle.ssh.api.Remote
import org.hidetake.gradle.ssh.api.operation.BadExitStatusException
import org.hidetake.gradle.ssh.api.operation.OperationSettings
import org.hidetake.gradle.ssh.api.ssh.BackgroundCommandException
import org.hidetake.gradle.ssh.api.ssh.Connection

/**
 * A default implementation of SSH connection.
 *
 * @author hidetake.org
 */
@Slf4j
class DefaultConnection implements Connection {
    final Remote remote

    private final Session session
    private final List<Channel> channels = []
    private final List<Closure> callbackForClosedChannels = []

    def DefaultConnection(Remote remote1, Session session1) {
        remote = remote1
        session = session1
        assert remote
        assert session
    }

    @Override
    ChannelExec createExecutionChannel(String command, OperationSettings operationSettings) {
        def channel = session.openChannel('exec') as ChannelExec
        channel.command = command
        channel.pty = operationSettings.pty
        channels.add(channel)
        channel
    }

    @Override
    ChannelShell createShellChannel(OperationSettings operationSettings) {
        def channel = session.openChannel('shell') as ChannelShell
        channels.add(channel)
        channel
    }

    @Override
    ChannelSftp createSftpChannel() {
        def channel = session.openChannel('sftp') as ChannelSftp
        channels.add(channel)
        channel
    }

    @Override
    void whenClosed(Channel channel, Closure closure) {
        boolean executed = false
        callbackForClosedChannels.add { ->
            if (!executed && channel.closed) {
                executed = true
                closure(channel)
            }
        }
    }

    @Override
    void executeCallbackForClosedChannels() {
        List<Exception> exceptions = []
        callbackForClosedChannels.each { callback ->
            try {
                callback.call()
            } catch (Exception e) {
                exceptions.add(e)
                if (e instanceof BadExitStatusException) {
                    log.warn("${e.class.name}: ${e.localizedMessage}")
                } else {
                    log.warn('Error in background command execution', e)
                }
            }
        }
        if (!exceptions.empty) {
            throw new BackgroundCommandException(exceptions)
        }
    }

    @Override
    boolean isAnyPending() {
        channels.any { channel -> !channel.closed }
    }

    @Override
    void cleanup() {
        channels*.disconnect()
        channels.clear()
    }
}
