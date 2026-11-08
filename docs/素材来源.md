# 素材来源与授权

| 素材 | 来源 | 授权 | 用在哪里 |
|---|---|---|---|
| 14 个人偶、留声机、缝纫机、煤油灯、翡翠吊坠（含首饰盒）的 3D 模型 | 先用火山方舟 Seedream 5.0 生成参考图，再用 Doubao-Seed3D 2.0 转成 3D 模型，由 `tools/blender/prep_ai.py` 减到 0.6～1.4 万面、贴图 1024 | AI 生成，本项目自有 | 三个案子的人物和部分道具 |
| 第 1 案画架上的钴蓝油画、客堂墙上的月份牌 | 火山方舟 Seedream 5.0 生成 | AI 生成，本项目自有 | 现场里的两幅画 |
| 墙面、砖、地板、瓦、木纹、地毯、布料、金属、碎石贴图 | Poly Haven（polyhaven.com），1K 版本 | CC0 | 现场建筑和家具，由 `tools/make_cases.py` 程序化生成 |
| 音效（拿起、放下、开抽屉、掀屋顶、新线索、翻页、推理成功、指认错误、结案） | Kenney「RPG Audio」「Interface Sounds」「Impact Sounds」（kenney.nl） | CC0 | 用 ffmpeg 剪短、转单声道 |
| 背景音乐《Moil》（Ruskerdax） | OpenGameArt（opengameart.org/content/moil） | CC0 | 循环背景音乐，响度统一到 -20 LUFS；另外 2 首 CC0 备选在 `docs/音乐候选/` |
| 思源宋体 Noto Serif SC Bold | Google / Adobe | SIL Open Font License 1.1 | 模型上的文字（房租簿、酒簿、水牌、时刻表等），转成网格，不随应用分发字体文件 |

人物、店铺、案件均为虚构。上海石库门、江南古镇客栈、沪宁铁路夜车只是时代背景。
