package com.smarttasker.core.protocol

/**
 * Protocol command IDs shared between SmartTasker and lxb-core.
 * Ported from AutoLXB's CommandIds.java.
 */
object CommandIds {
    // Link layer
    const val CMD_HANDSHAKE: Byte = 0x01
    const val CMD_ACK: Byte = 0x02
    const val CMD_HEARTBEAT: Byte = 0x03

    // Input layer
    const val CMD_TAP: Byte = 0x10
    const val CMD_SWIPE: Byte = 0x11
    const val CMD_LONG_PRESS: Byte = 0x12
    const val CMD_INPUT_TEXT: Byte = 0x20
    const val CMD_KEY_EVENT: Byte = 0x21

    // Sense layer
    const val CMD_DUMP_HIERARCHY: Byte = 0x31
    const val CMD_GET_SCREEN_SIZE: Byte = 0x37

    // Lifecycle
    const val CMD_LAUNCH_APP: Byte = 0x43

    // Media
    const val CMD_SCREENSHOT: Byte = 0x60

    // Cortex FSM (0x70-0x7F)
    const val CMD_MAP_SET_GZ: Byte = 0x70
    const val CMD_MAP_GET_INFO: Byte = 0x71
    const val CMD_CORTEX_RESOLVE_LOCATOR: Byte = 0x72
    const val CMD_CORTEX_TAP_LOCATOR: Byte = 0x73
    const val CMD_CORTEX_TRACE_PULL: Byte = 0x74
    const val CMD_CORTEX_ROUTE_RUN: Byte = 0x75
    const val CMD_CORTEX_FSM_RUN: Byte = 0x76
    const val CMD_CORTEX_TASK_STATUS: Byte = 0x77
    const val CMD_CORTEX_FSM_CANCEL: Byte = 0x78
    const val CMD_CORTEX_TASK_LIST: Byte = 0x79
    const val CMD_CORTEX_SCHEDULE_ADD: Byte = 0x7A
    const val CMD_CORTEX_SCHEDULE_LIST: Byte = 0x7B
    const val CMD_CORTEX_SCHEDULE_REMOVE: Byte = 0x7C
    const val CMD_CORTEX_SCHEDULE_UPDATE: Byte = 0x7D
    const val CMD_CORTEX_NOTIFY: Byte = 0x7E
    const val CMD_CORTEX_TASK_MAP: Byte = 0x7F
}
