# 音效資源（res/raw/）

## 預期檔案

| 檔名             | 用途           | 觸發時機                           |
| ---------------- | -------------- | ---------------------------------- |
| `sfx_chip.ogg`   | 籌碼下注       | 玩家或 AI 執行 CALL / RAISE        |
| `sfx_deal.ogg`   | 發牌           | 新的一手開始                       |
| `sfx_button.ogg` | 按鈕點擊       | 玩家執行 FOLD / CHECK / UI 按鈕    |

## 規格建議

- 格式：**OGG Vorbis**（Android 原生支援；體積小、解壓低開銷）
- 取樣率：**44.1 kHz**
- 聲道：**Mono**（手機/平板喇叭一般不分離）
- 長度：**< 200ms**（避免遮蓋下一個動作）
- 大小：**< 30 KB** 每個（SoundPool 友善）
- 音量：標準化到 -3 dBFS（避免裁切）

## 放置位置

```
app/src/main/res/raw/
    sfx_chip.ogg
    sfx_deal.ogg
    sfx_button.ogg
```

## 取得來源建議

> 註：Pingu 動畫的音效屬於 Joker Inc. / The Pygos Group 著作，公開散布有風險。
> 建議自行錄製類似風格（口哨／嗶嗶聲），或從 CC0 音效庫取得：
>
> - https://freesound.org/ （搜尋 "pop", "chip"；過濾 CC0）
> - https://kenney.nl/assets （遊戲音效，CC0）
> - https://opengameart.org/ （多為 CC-BY / CC0）

## 缺檔行為

`SoundManager` 載入失敗時靜默 fallback：缺檔不會 crash，只是該音效不會播放。
所以你可以先把 app 跑起來測試流程，再陸續補上音效檔。

## 轉檔指令參考（ffmpeg）

```bash
ffmpeg -i input.wav -ac 1 -ar 44100 -c:a libvorbis -qscale:a 4 sfx_chip.ogg
```
