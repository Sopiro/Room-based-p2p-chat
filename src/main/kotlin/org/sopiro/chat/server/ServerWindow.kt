package org.sopiro.chat.server

import org.sopiro.chat.server.room.RoomManager
import org.sopiro.chat.utils.Logger
import org.sopiro.chat.utils.Parser
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import javax.swing.text.DefaultCaret


class ServerWindow(title: String) : Server()
{
    private var window: JFrame = JFrame(title)
    private var body: JPanel
    private var foot: JPanel
    private var screen: JTextArea
    private var scroller: JScrollPane
    private var enterBtn: JButton
    private var cmdLine: JTextField

    private var logger: Logger

    private val defaultMsg = "start -p 1234"

    init
    {
        // JFrame settings
        window.isResizable = false
        window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        (window.contentPane as JComponent).border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // Define controls
        body = JPanel()
        body.layout = BorderLayout()

        foot = JPanel()
        foot.layout = FlowLayout(FlowLayout.RIGHT)

        screen = JTextArea(20, 80)
        logger = Logger(screen)

        screen.lineWrap = true
        screen.isEditable = false
        (screen.caret as DefaultCaret).updatePolicy = DefaultCaret.ALWAYS_UPDATE
        scroller = JScrollPane(screen, 20, 30)

        enterBtn = JButton("Enter")
        enterBtn.addActionListener {
            interpret()
        }

        cmdLine = JTextField(80)
        cmdLine.addActionListener {
            enterBtn.doClick()
        }

        body.add(scroller, BorderLayout.CENTER)
        foot.add(cmdLine)
        foot.add(enterBtn)

        cmdLine.text = defaultMsg

        window.add(body, BorderLayout.CENTER)
        window.add(foot, BorderLayout.SOUTH)

        window.pack()

        window.addWindowListener(object : WindowAdapter()
        {
            override fun windowClosing(e: WindowEvent)
            {
                super.windowClosing(e)
                terminate()
            }

        })

        window.setLocationRelativeTo(null)

        cmdLine.requestFocus()

        window.isVisible = true

        init()
    }

    private fun init()
    {
        RoomManager.newRoom("127.0.0.1", 1234, "테스트 방1", "나다1", 10)
        RoomManager.newRoom("127.0.0.1", 1234, "테스트 방2", "나다2", 10)
        RoomManager.newRoom("127.0.0.1", 1234, "테스트 방3", "나다3", 10)
    }

    private fun interpret()
    {
        val rawText = cmdLine.text
        val parser = Parser(rawText)

        when (parser.cmd)
        {
            "start" ->
            {
                try
                {
                    val port = Integer.parseInt(parser.getOption("p"))
                    if (!super.start(port))
                    {
                        logger.log("Server is already Online")
                    }
                } catch (e: NumberFormatException)
                {
                    logger.log("Sets port with -p option correctly")
                }
            }

            "error" ->
            {
                logger.log("Error: $rawText")
            }

            "ls" ->
            {
                logger.log("$numClients Clients are existing, ${RoomManager.howMany()} Rooms exist")
            }

            "cls" ->
            {
                screen.text = ""
            }

            "noti" ->
            {
                if (parser.getOption("m") == null) return

                val msg = parser.getOption("m").toString()
                notifyToAll(msg)
                logger.log("Notified to all clients: $msg")
            }

            "exit" ->
            {
                terminate()
                window.dispose()
            }

            else -> logger.log(rawText)
        }

        cmdLine.text = ""
    }

    override fun onStartServer(port: Int)
    {
        logger.log("Server started on port: $port")
    }

    override fun onWaitClientAccess()
    {
        logger.log("Waiting client's access")
    }

    override fun onClientConnect(handle: ClientHandle)
    {
        logger.log("Got one ${handle.socket.localAddress}")

        // Send room info
        sendRoomInfo(handle)
    }

    override fun onReceiveData(handle: ClientHandle, parser: Parser)
    {
        when (parser.cmd)
        {
            "msg" ->
            {
                logger.log(parser.getOption("m").toString())
            }

            "newRoom" ->
            {
                val ip = handle.socket.inetAddress.hostAddress
                val port = Integer.parseInt(parser.getOption("p"))
                val rn = parser.getOption("rn")
                val hn = parser.getOption("hn")

                println(parser.options)

                RoomManager.newRoom(ip, port, rn!!, hn!!, 1)

                sendRoomInfo(handle)

                logger.log("${handle.socket.localAddress} requests new room")
                logger.logNoTime("RoomName: $rn")
                logger.logNoTime("HostName: $hn")
            }

            "refresh" ->
            {
                sendRoomInfo(handle)
            }

            else -> logger.log(parser.str)
        }
    }

    override fun onClientDisconnect(handle: ClientHandle)
    {
        logger.log("${handle.socket.inetAddress} goes out")

        super.onClientDisconnect(handle)
    }

    private fun notifyToAll(message: String)
    {
        super.sendToAll("noti -m \"$message\"")
    }

    private fun sendRoomInfo(handle: ClientHandle)
    {
        send(handle, RoomManager.getRoomInfo())
    }
}
