# HID 按键映射表

本文档记录了 `HidKeyboardDef` 中定义的 16 进制按键值（HID Code）、转换后的 10 进制按键值与其对应的具体按键名称（Key Name）。

## 1. 主键盘 - 功能行 (Function Row)

| HID 按键值 (Hex) | 10进制值 (Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `0x29` | 41 | Esc | 
| `0x3A` | 58 | F1 | 
| `0x3B` | 59 | F2 | 
| `0x3C` | 60 | F3 | 
| `0x3D` | 61 | F4 | 
| `0x3E` | 62 | F5 | 
| `0x3F` | 63 | F6 | 
| `0x40` | 64 | F7 | 
| `0x41` | 65 | F8 | 
| `0x42` | 66 | F9 | 
| `0x43` | 67 | F10 | 
| `0x44` | 68 | F11 | 
| `0x45` | 69 | F12 | 
| `0x46` | 70 | PrtSc | 
| `0x47` | 71 | ScrLk | 
| `0x48` | 72 | Pause | 

## 2. 数字行 (Number Row)

| HID 按键值 (Hex) | 10进制值 (Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `0x35` | 53 | \` | 
| `0x1E` | 30 | 1 | 
| `0x1F` | 31 | 2 | 
| `0x20` | 32 | 3 | 
| `0x21` | 33 | 4 | 
| `0x22` | 34 | 5 | 
| `0x23` | 35 | 6 | 
| `0x24` | 36 | 7 | 
| `0x25` | 37 | 8 | 
| `0x26` | 38 | 9 | 
| `0x27` | 39 | 0 | 
| `0x2D` | 45 | \- | 
| `0x2E` | 46 | = | 
| `0x2A` | 42 | Backspace | 

## 3. Q 行 (Q Row)

| HID 按键值 (Hex) | 10进制值 (Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `0x2B` | 43 | Tab | 
| `0x14` | 20 | Q | 
| `0x1A` | 26 | W | 
| `0x08` | 8 | E | 
| `0x15` | 21 | R | 
| `0x17` | 23 | T | 
| `0x1C` | 28 | Y | 
| `0x18` | 24 | U | 
| `0x0C` | 12 | I | 
| `0x12` | 18 | O | 
| `0x13` | 19 | P | 
| `0x2F` | 47 | \[ | 
| `0x30` | 48 | \] | 
| `0x31` | 49 | \\ | 

## 4. A 行 (A Row)

| HID 按键值 (Hex) | 10进制值 (Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `0x39` | 57 | Caps | 
| `0x04` | 4 | A | 
| `0x16` | 22 | S | 
| `0x07` | 7 | D | 
| `0x09` | 9 | F | 
| `0x0A` | 10 | G | 
| `0x0B` | 11 | H | 
| `0x0D` | 13 | J | 
| `0x0E` | 14 | K | 
| `0x0F` | 15 | L | 
| `0x33` | 51 | ; | 
| `0x34` | 52 | ' | 
| `0x28` | 40 | Enter | 

## 5. Z 行 (Z Row)

| HID 按键值 (Hex) | 10进制值 (Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `0xE1` | 225 | Shift (Left) | 
| `0x1D` | 29 | Z | 
| `0x1B` | 27 | X | 
| `0x06` | 6 | C | 
| `0x19` | 25 | V | 
| `0x05` | 5 | B | 
| `0x11` | 17 | N | 
| `0x10` | 16 | M | 
| `0x36` | 54 | , | 
| `0x37` | 55 | . | 
| `0x38` | 56 | / | 
| `0xE5` | 229 | Shift (Right) | 

## 6. 底行 (Bottom Row)

| HID 按键值 (Hex) | 10进制值 (Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `0xE0` | 224 | Ctrl (Left) | 
| `0xE3` | 227 | Start (Left Win) | 
| `0xE2` | 226 | Alt (Left) | 
| `0x2C` | 44 | Space | 
| `0xE6` | 230 | Alt (Right) | 
| `0xE7` | 231 | Start (Right Win) | 
| `0x65` | 101 | Menu | 
| `0xE4` | 228 | Ctrl (Right) | 

## 7. 编辑区 (Control/Editing Keys)

| HID 按键值 (Hex) | 10进制值 (Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `0x49` | 73 | Ins | 
| `0x4A` | 74 | Home | 
| `0x4B` | 75 | PgUp | 
| `0x4C` | 76 | Del | 
| `0x4D` | 77 | End | 
| `0x4E` | 78 | PgDn | 

## 8. 方向键 (Navigation Keys)

| HID 按键值 (Hex) | 10进制值 (Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `0x52` | 82 | Up | 
| `0x50` | 80 | Left | 
| `0x51` | 81 | Down | 
| `0x4F` | 79 | Right | 

## 9. 数字小键盘 (Numpad)

| HID 按键值 (Hex) | 10进制值 (Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `0x53` | 83 | Num Lock | 
| `0x54` | 84 | / | 
| `0x55` | 85 | \* | 
| `0x56` | 86 | \- | 
| `0x5F` | 95 | 7 | 
| `0x60` | 96 | 8 | 
| `0x61` | 97 | 9 | 
| `0x57` | 87 | + | 
| `0x5C` | 92 | 4 | 
| `0x5D` | 93 | 5 | 
| `0x5E` | 94 | 6 | 
| `0x59` | 89 | 1 | 
| `0x5A` | 90 | 2 | 
| `0x5B` | 91 | 3 | 
| `0x58` | 88 | Enter | 
| `0x62` | 98 | 0 | 
| `0x63` | 99 | . | 

## 10. 系统控制键 (System Controls)

*(注：`SYSTEM_KEY_OFFSET` 为系统自定义偏移量)*

| HID 按键表达式 | 偏移值 (Hex / Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `SYSTEM_KEY_OFFSET + 0x81` | 0x81 / 129 | Power | 
| `SYSTEM_KEY_OFFSET + 0x82` | 0x82 / 130 | Sleep | 
| `SYSTEM_KEY_OFFSET + 0x83` | 0x83 / 131 | Wake | 
| `CONSUMER_KEY_OFFSET + 0x6F` | 0x6F / 111 | Brightness+ | 
| `CONSUMER_KEY_OFFSET + 0x70` | 0x70 / 112 | Brightness- | 
| `CONSUMER_KEY_OFFSET + 0xB1` | 0xB1 / 177 | Display Toggle | 

## 11. 多媒体键 (Media Keys)

*(注：`CONSUMER_KEY_OFFSET` 为消费电子设备键值偏移量)*

| HID 按键表达式 | 偏移值 (Hex / Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `CONSUMER_KEY_OFFSET + 0xB6` | 0xB6 / 182 | Previous | 
| `CONSUMER_KEY_OFFSET + 0xB5` | 0xB5 / 181 | Next | 
| `CONSUMER_KEY_OFFSET + 0xCD` | 0xCD / 205 | Play/Pause | 
| `CONSUMER_KEY_OFFSET + 0xB7` | 0xB7 / 183 | Stop | 
| `CONSUMER_KEY_OFFSET + 0xB4` | 0xB4 / 180 | Rewind | 
| `CONSUMER_KEY_OFFSET + 0xB3` | 0xB3 / 179 | Fast Forward | 
| `CONSUMER_KEY_OFFSET + 0xE9` | 0xE9 / 233 | Volume+ | 
| `CONSUMER_KEY_OFFSET + 0xEA` | 0xEA / 234 | Volume- | 
| `CONSUMER_KEY_OFFSET + 0xE2` | 0xE2 / 226 | Mute | 

## 12. 浏览器键 (Browser Keys)

| HID 按键表达式 | 偏移值 (Hex / Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `CONSUMER_KEY_OFFSET + 0x224` | 0x224 / 548 | Back | 
| `CONSUMER_KEY_OFFSET + 0x225` | 0x225 / 549 | Forward | 
| `CONSUMER_KEY_OFFSET + 0x227` | 0x227 / 551 | Refresh | 
| `CONSUMER_KEY_OFFSET + 0x226` | 0x226 / 550 | Stop | 
| `CONSUMER_KEY_OFFSET + 0x221` | 0x221 / 545 | Search | 
| `CONSUMER_KEY_OFFSET + 0x222` | 0x222 / 546 | Favorites | 
| `CONSUMER_KEY_OFFSET + 0x223` | 0x223 / 547 | Home | 

## 13. 应用启动键 (App Launchers)

| HID 按键表达式 | 偏移值 (Hex / Dec) | 按键名称 | 
| ----- | ----- | ----- | 
| `CONSUMER_KEY_OFFSET + 0x18A` | 0x18A / 394 | Mail | 
| `CONSUMER_KEY_OFFSET + 0x192` | 0x192 / 402 | Calculator | 
| `CONSUMER_KEY_OFFSET + 0x183` | 0x183 / 387 | MediaPlayer | 
| `CONSUMER_KEY_OFFSET + 0x194` | 0x194 / 404 | MyComputer | 
| `CONSUMER_KEY_OFFSET + 0x221` | 0x221 / 545 | Search | 
