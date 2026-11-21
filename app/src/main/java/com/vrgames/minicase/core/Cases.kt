package com.vrgames.minicase.core

import com.vrgames.minicase.core.Action.EXAMINE
import com.vrgames.minicase.core.Action.LIFT
import com.vrgames.minicase.core.Action.OPEN
import com.vrgames.minicase.core.ClueKind.DEDUCTION
import com.vrgames.minicase.core.ClueKind.EVIDENCE
import com.vrgames.minicase.core.ClueKind.TESTIMONY

/**
 * The four cases, easiest first. All people, places and firms are invented; the settings (a
 * 1930s Shanghai lane house, a late-Qing water-town inn, a 1930s night train, a 1930s Peiping
 * theatre's backstage) are only backdrops.
 * Prop ids are node names in the case's model (tools/make_case*.py).
 */
object Cases {
    val all: List<Case> by lazy { listOf(wutong(), inn(), train(), opera()) }

    fun byId(id: String) = all.first { it.id == id }

    private fun e(id: String, title: String, text: String) = Clue(id, EVIDENCE, title, text)
    private fun t(id: String, title: String, text: String) = Clue(id, TESTIMONY, title, text)
    private fun d(id: String, title: String, text: String) = Clue(id, DEDUCTION, title, text)

    // ---- 1. 梧桐里的怀表 -------------------------------------------------------------------------

    private fun wutong() = Case(
        id = "wutong",
        number = 1,
        title = "梧桐里的怀表",
        place = "上海 · 梧桐里七号石库门",
        time = "民国二十四年冬",
        brief = "房东周先生的祖传金怀表，昨晚九点锁进客堂写字台的抽屉，今早九点就不见了。夜里大门上了闩，没有外人进来过。" +
            "这栋石库门里住着：亭子间的裁缝林太太、前楼的画家陈先生，还有帮佣的少年阿福。",
        props = listOf(
            Prop("drawer", "写字台抽屉", OPEN, gives = listOf("pry"), where = "客堂写字台的抽屉"),
            Prop("watchbox", "空表盒", EXAMINE, gives = listOf("paint"), needs = listOf("drawer"), hiddenSide = Side.BOTTOM, where = "抽屉里的空表盒（翻过来看看底部）", look = "周先生放金怀表的红色小盒。"),
            Prop("rentbook", "房租簿", EXAMINE, gives = listOf("rent"), where = "客堂写字台上的房租簿", look = "周先生记各间房客交租的簿子。"),
            Prop("easel", "画架上的油画", EXAMINE, gives = listOf("blue"), where = "前楼画架上的油画", look = "陈先生摆在前楼画架上的画。"),
            Prop("knife", "调色刀", EXAMINE, gives = listOf("knife"), hiddenSide = Side.RIGHT, where = "前楼桌上的调色刀（转过来看看刀刃）", look = "画油画时调颜料用的小刀。"),
            Prop("coat", "旧西装", EXAMINE, gives = listOf("ticket"), hiddenSide = Side.BACK, where = "前楼衣架上的旧西装（转过去看看口袋）", look = "衣架上一件洗旧了的西装。"),
            Prop("sewing", "缝纫机", EXAMINE, gives = listOf("order"), where = "亭子间的缝纫机", look = "亭子间林太太干活的缝纫机。"),
            Prop("lamp", "煤油灯", EXAMINE, gives = listOf("lamp"), where = "亭子间的煤油灯", look = "亭子间里的一盏旧油灯。"),
            Prop("shoes", "布鞋", EXAMINE, gives = listOf("mud"), hiddenSide = Side.BOTTOM, where = "灶披间里阿福的布鞋（翻过来看鞋底）", look = "阿福放在灶披间的一双布鞋。"),
            Prop("herbs", "药包", EXAMINE, gives = listOf("medicine"), where = "灶披间灶台上的药包", look = "灶台上一个用麻绳扎好的纸包。"),
        ),
        people = listOf(
            Person(
                "zhou", "周先生", "房东", "六十岁，这栋石库门的房东。怀表是他父亲留下的，每天早上九点他都要给它上弦。",
                suspect = false,
                questions = listOf(
                    Question("zhou_when", "怀表是什么时候放进抽屉的？", "昨晚九点。我给它上好弦，放进表盒，锁进写字台的抽屉。抽屉钥匙一直挂在我裤腰上，睡觉也不摘。", gives = listOf("zhou_key")),
                    Question("zhou_keys", "家里的钥匙都在谁手上？", "大门和各间房的钥匙，阿福那里有一串，他要打扫。可写字台的钥匙只有一把，就在我身上。", gives = listOf("keys")),
                    Question("zhou_rent", "陈先生欠了房租？", "三个月了！我说月底再不交就请他搬走。昨天下午他还来求我宽限，我没答应。", needs = listOf("rent")),
                ),
            ),
            Person(
                "lin", "林太太", "亭子间的裁缝", "四十岁，靠给人做旗袍过日子，手艺在弄堂里出名。",
                questions = listOf(
                    Question("lin_night", "昨晚你在做什么？", "我十点就熄灯睡了，什么也没听见。"),
                    Question("lin_lamp", "你的煤油灯快烧干了，昨晚真的十点就睡了？", "……好吧，我没睡。张公馆的旗袍今早九点要取，我一直赶到后半夜一点。" +
                        "十一点半左右，我听见客堂的门吱呀一声，接着有人轻手轻脚地上楼，往前楼去了。", needs = listOf("lamp"), gives = listOf("creak")),
                    Question("lin_order", "这张订单是怎么回事？", "张公馆订的两件旗袍，今早九点来取。我要是交不出，以后就没人找我做了。", needs = listOf("order")),
                ),
            ),
            Person(
                "afu", "阿福", "帮佣的少年", "十六岁，从乡下来上海帮佣，住在楼下的灶披间，手上有一串房门钥匙。",
                questions = listOf(
                    Question("afu_night", "昨晚你在哪儿？", "我、我九点多就睡下了，一直睡到天亮。"),
                    Question("afu_mud", "你鞋底的新泥是哪来的？", "……我说实话。我娘咳得厉害，我十点多偷偷溜出去，到同仁堂抓了一副药，十一点前就回来睡下了。" +
                        "我没有写字台的钥匙，真的不是我！", needs = listOf("mud"), gives = listOf("afu_out")),
                    Question("afu_pry", "抽屉是被撬开的，你知道什么吗？", "撬开的？可我连写字台的钥匙都没有……我从来不碰周先生的东西。", needs = listOf("pry")),
                ),
            ),
            Person(
                "chen", "陈先生", "前楼的画家", "三十岁，美专毕业，画卖不出去，住在前楼。",
                questions = listOf(
                    Question("chen_night", "昨晚你在做什么？", "在前楼画画，一夜都没出门。画家晚上灵感最好。"),
                    Question("chen_rent", "你欠了周先生三个月房租？", "艺术家哪有不穷的……周先生催得紧，月底再交不上，我就得搬走了。", needs = listOf("rent")),
                    Question("chen_ticket", "你西装口袋里这张当票是怎么回事？", "那、那是我自己的表！……我不想再说了。", needs = listOf("ticket")),
                ),
            ),
        ),
        clues = listOf(
            e("pry", "抽屉上的撬痕", "抽屉锁舌边的木头翻起了毛刺，是新撬的。锁不是用钥匙打开的。"),
            e("paint", "表盒底的油彩", "表盒底部沾着一抹钴蓝色的油彩，还没完全干透。"),
            e("rent", "房租簿", "前楼陈先生：八月、九月、十月，三个月未交。周先生在旁边批了一行字：月底再不交，请其搬走。"),
            e("blue", "钴蓝色的天空", "画布上是一大片钴蓝色的天空。这种进口颜料很贵，整条弄堂只有陈先生在用。"),
            e("knife", "卷了口的调色刀", "调色刀的刀尖卷了口，刃上还卡着一丝深色的木屑。"),
            e("ticket", "恒昌当铺的当票", "恒昌当铺当票：今晨八点一刻，金怀表一只，押洋四十元。当户署名：陈。"),
            e("order", "张公馆的订单", "缝纫机上压着一张订单：张公馆旗袍两件，今早九点取货。"),
            e("lamp", "烧干的煤油灯", "煤油灯里的油几乎烧干了，玻璃灯罩熏得发黑，昨夜点了很久。"),
            e("mud", "鞋底的新泥", "布鞋底糊着一层新泥。昨夜下过雨，穿这双鞋的人出过门。"),
            e("medicine", "同仁堂的药包", "一包中药，药方上写着：咳喘方，同仁堂，昨夜十点半配。"),
            t("zhou_key", "周先生：钥匙不离身", "昨晚九点怀表锁进抽屉，唯一的抽屉钥匙一直挂在周先生裤腰上。"),
            t("keys", "周先生：谁有钥匙", "阿福有大门和各间房的钥匙，可没有写字台的钥匙。"),
            t("creak", "林太太：十一点半的脚步声", "林太太赶活到后半夜一点。十一点半左右，她听见客堂门响，有人轻手轻脚地上楼，往前楼去了。"),
            t("afu_out", "阿福：出门抓药", "阿福十点多出门给母亲抓药，十一点前回来睡下。"),
            d("forced", "推论：拿表的人没有钥匙", "抽屉是撬开的，而唯一的钥匙一直在周先生身上——拿表的人没有钥匙，只能硬撬。"),
            d("paint_chen", "推论：油彩来自陈先生", "表盒底的钴蓝油彩，正是整条弄堂只有陈先生在用的那种颜料。碰过表盒的手，刚碰过他的颜料。"),
            d("knife_pry", "推论：调色刀撬开了抽屉", "调色刀卷了口、卡着深色木屑，和抽屉上新撬的痕迹对得上：抽屉是用陈先生的调色刀撬开的。"),
            d("night_route", "推论：十一点半上楼的人", "阿福十一点前已经回来睡下，林太太在亭子间赶活。十一点半从客堂上楼、去了前楼的，只能是陈先生。"),
        ),
        deductions = listOf(
            Deduction("pry", "zhou_key", "forced"),
            Deduction("paint", "blue", "paint_chen"),
            Deduction("knife", "pry", "knife_pry"),
            Deduction("creak", "afu_out", "night_route"),
        ),
        solution = Solution(
            culprit = "chen",
            motive = setOf("rent"),
            proof = setOf("ticket", "paint_chen", "knife_pry", "night_route"),
            wrong = mapOf(
                "lin" to "林太太昨夜一直在亭子间赶活，她的证词反倒说明，十一点半有人从客堂上了前楼。",
                "afu" to "阿福没有写字台的钥匙，可抽屉是被撬开的；他出门抓药，十一点前就回来了，鞋底的泥说明不了他碰过抽屉。",
            ),
        ),
        truth = "陈先生欠了三个月房租，月底就要被赶走。昨夜十一点半，他趁全家睡下，摸进客堂，用调色刀撬开写字台的抽屉，拿走了怀表——" +
            "手上没洗净的钴蓝油彩蹭在了表盒底上。今早八点一刻，他把怀表当进了恒昌当铺，想先凑上房租。" +
            "周先生拿着当票赎回了怀表，没有报警，只请陈先生搬走。",
    )

    // ---- 2. 听雨客栈 ---------------------------------------------------------------------------

    private fun inn() = Case(
        id = "inn",
        number = 2,
        title = "听雨客栈的银票",
        place = "江南古镇 · 听雨客栈",
        time = "光绪年间，一个雨夜",
        brief = "盐商赵老板住在听雨客栈楼上的天字二号房。晚上七点他下楼吃饭听书，八点半回房，锁着的樟木箱里三张银票不翼而飞，一共三千两。" +
            "雨下了一夜，客栈前后门都没开过。当晚在客栈里的有：钱掌柜、跑堂小六、说书的吴先生，和住在隔壁天字三号房的女学生郑小姐。",
        props = listOf(
            Prop("roof", "客房的屋顶", LIFT, where = "后排客房的屋顶（捏住往上提，看看楼上的客房）"),
            Prop("trunk", "樟木箱", OPEN, gives = listOf("trunk"), needs = listOf("roof"), where = "天字二号房的樟木箱"),
            Prop("letter", "一封信", EXAMINE, gives = listOf("letter"), needs = listOf("roof"), where = "天字三号房桌上的信", look = "压在天字三号房桌上的信笺。"),
            Prop("keyboard", "钥匙板", EXAMINE, gives = listOf("hook"), where = "柜台后墙上的钥匙板", look = "柜台后墙上挂各房钥匙的木板。"),
            Prop("winelog", "酒簿", EXAMINE, gives = listOf("winelog"), where = "柜台上的酒簿", look = "柜台上记每天卖酒的账簿。"),
            Prop("counterdrawer", "柜台抽屉", OPEN, where = "柜台的抽屉"),
            Prop("loan", "借据", EXAMINE, gives = listOf("loan"), needs = listOf("counterdrawer"), where = "柜台抽屉里的借据", look = "折好压在抽屉底的一张纸。"),
            Prop("board", "水牌", EXAMINE, gives = listOf("program"), where = "说书台边的水牌", look = "说书台边挂着的一块木牌。"),
            Prop("sandal", "草鞋", EXAMINE, gives = listOf("steps"), hiddenSide = Side.BOTTOM, where = "后楼梯拐角的草鞋（翻过来看鞋底）", look = "丢在楼梯拐角的一只旧草鞋。"),
            Prop("stove", "灶门", OPEN, where = "灶房的灶门"),
            Prop("tinbox", "铁皮盒", EXAMINE, gives = listOf("notes"), needs = listOf("stove"), where = "灶膛冷灰里的铁皮盒", look = "一只生了锈、扣得紧紧的扁盒。"),
            Prop("pillow", "枕头", EXAMINE, gives = listOf("iou"), hiddenSide = Side.BOTTOM, where = "灶房角落小六铺盖上的枕头（翻过来看看）", look = "小六铺盖上的荞麦皮枕头。"),
        ),
        people = listOf(
            Person(
                "zhao", "赵老板", "盐商", "五十岁，从扬州来收账，明早就要搭船回去。",
                suspect = false,
                questions = listOf(
                    Question("zhao_when", "银票是什么时候不见的？", "七点我把银票锁进樟木箱才下楼，吃饭听书，八点半回房一开箱，空了！箱子钥匙一直拴在我腰带上。", gives = listOf("zhao_key")),
                    Question("zhao_spare", "还有别的钥匙能开你的房门和箱子吗？", "客栈每间房都配一把备用钥匙，房门和箱子用的是同一把，都挂在柜台后面的钥匙板上。", gives = listOf("spare")),
                    Question("zhao_loan", "钱掌柜欠你钱？", "二百两，秋后还。他做生意一向守信，我没催过他。", needs = listOf("loan")),
                ),
            ),
            Person(
                "qian", "钱掌柜", "客栈掌柜", "五十五岁，在镇上开了三十年客栈，精明，爱面子。",
                questions = listOf(
                    Question("qian_night", "晚上你在哪儿？", "一直在柜台，哪儿也没去，天地良心。"),
                    Question("qian_wine", "酒簿上写着你八点亲自去取酒？", "哦，对。八点我下酒窖取了一坛黄酒，前后一刻钟，柜台交给小六看着。这事我倒忘了。", needs = listOf("winelog"), gives = listOf("cellar")),
                    Question("qian_loan", "你欠赵老板二百两？", "是欠着，秋后就还。为二百两毁了我三十年的招牌？我还没糊涂到那个地步。", needs = listOf("loan")),
                ),
            ),
            Person(
                "xiaoliu", "小六", "跑堂", "十八岁，手脚麻利，夜里就睡在灶房角落的铺盖上。",
                questions = listOf(
                    Question("xl_night", "晚上你在做什么？", "一直在大堂端茶倒水，吴先生说书的时候我一步也没离开。"),
                    Question("xl_steps", "后楼梯上的草鞋是你的吧？", "我、我去后院收衣裳，雨大，就从灶房走了后楼梯……我没上二楼！", needs = listOf("steps")),
                    Question("xl_iou", "这张赌坊的欠条是怎么回事？", "……这跟银票没关系！你别瞎猜！", needs = listOf("iou")),
                ),
            ),
            Person(
                "wu", "吴先生", "说书先生", "六十岁，走街串巷说书，这几天在听雨客栈的大堂开讲。",
                questions = listOf(
                    Question("wu_night", "晚上你在做什么？", "在说书台上讲《三侠五义》，从七点半讲到八点半，中间一口茶都没顾上喝。"),
                    Question("wu_saw", "说书的时候，你看见大堂里有什么动静吗？", "说书的人眼观六路。八点刚过，掌柜的抱着空坛下了酒窖，柜台交给小六。" +
                        "可没一会儿，小六也不见了，过了好一阵才从灶房那边回来，裤脚湿了半截。掌柜的是一刻钟后抱着满坛子从酒窖上来的。", needs = listOf("program"), gives = listOf("wu_saw")),
                ),
            ),
            Person(
                "zheng", "郑小姐", "女学生", "二十岁，从省城回乡探亲，住在天字三号房，就在赵老板隔壁。",
                questions = listOf(
                    Question("zheng_night", "晚上你在做什么？", "一直在房里看书，没出过门。"),
                    Question("zheng_letter", "这封信上写着「今夜八点，楼外柳下」？", "……求你别告诉我爹。八点我在晒台上等人。刚过八点，我看见隔壁天字二号的窗户里有灯光晃了一下，" +
                        "可赵老板明明还在楼下听书。我以为是小二进去添茶，就没在意。", needs = listOf("letter"), gives = listOf("light")),
                ),
            ),
        ),
        clues = listOf(
            e("trunk", "完好的箱锁", "樟木箱的铜锁好好的，没有一点撬痕——是用钥匙打开的。"),
            e("letter", "没署名的信", "一封没有署名的信：「今夜八点，楼外柳下，不见不散。」"),
            e("hook", "挂错的钥匙", "钥匙板上，天字二号房的备用钥匙挂在了天字三号的钩子上——有人用过它，匆忙放回去时挂错了。"),
            e("winelog", "酒簿", "酒簿最后一行：今晚八点，掌柜亲取黄酒一坛。"),
            e("loan", "借据", "借据：钱掌柜借赵老板纹银二百两，秋后归还。"),
            e("program", "水牌", "今晚书目《三侠五义》，七点半开讲，八点半散场。"),
            e("steps", "沾泥的草鞋", "草鞋底糊满后院的黄泥。从灶房到二楼的后楼梯上，一路都是同样的泥印。"),
            e("notes", "灶灰里的银票", "灶膛的冷灰下埋着一个铁皮盒，里面正是三张银票，边角沾着灶灰。"),
            e("iou", "赌坊的欠条", "枕头底下压着一张欠条：小六欠城隍庙后赌档纹银三十两，三日内还清。"),
            t("zhao_key", "赵老板：钥匙不离身", "七点银票锁进箱子，箱子钥匙一直拴在赵老板的腰带上。"),
            t("spare", "赵老板：备用钥匙", "每间房配一把备用钥匙，房门和箱子同用一把，挂在柜台后的钥匙板上。"),
            t("cellar", "钱掌柜：下酒窖", "八点掌柜下酒窖取酒，前后一刻钟，柜台交给小六照看。"),
            t("wu_saw", "吴先生：小六离开过", "八点刚过掌柜下了酒窖，柜台交给小六；没一会儿小六也不见了，过了好一阵才从灶房那边回来，裤脚湿了半截。掌柜一刻钟后抱着满坛子从酒窖上来。"),
            t("light", "郑小姐：窗里的灯光", "刚过八点，郑小姐在晒台上看见天字二号房里有灯光晃动，而赵老板还在楼下听书。"),
            d("spare_used", "推论：用的是备用钥匙", "箱锁完好，是用钥匙开的；赵老板的钥匙没离身——开箱的人用的是钥匙板上的备用钥匙。"),
            d("when", "推论：八点那一刻钟", "八点刚过天字二号房里有人；而那一刻钟掌柜在酒窖，柜台上的钥匙板只有小六看着。"),
            d("route", "推论：小六的去向", "小六在那一刻钟离开大堂，从灶房走后楼梯上了二楼——楼梯上的泥印就是他的草鞋踩的。"),
            d("qian_out", "推论：掌柜在酒窖", "酒簿和吴先生的话对得上：那一刻钟掌柜一直在酒窖，回来时抱着满坛子，没有上过楼。"),
        ),
        deductions = listOf(
            Deduction("trunk", "spare", "spare_used"),
            Deduction("light", "cellar", "when"),
            Deduction("wu_saw", "steps", "route"),
            Deduction("winelog", "wu_saw", "qian_out"),
        ),
        solution = Solution(
            culprit = "xiaoliu",
            motive = setOf("iou"),
            proof = setOf("route", "notes", "when"),
            wrong = mapOf(
                "qian" to "那一刻钟掌柜在酒窖取酒，吴先生看着他抱着满坛子上来；二百两的借据秋后才到期，他犯不着砸自己的招牌。",
                "wu" to "吴先生从七点半到八点半一直在说书台上，满堂的客人都是见证。",
                "zheng" to "郑小姐那时在晒台上等人，她看见的灯光，正是有人在隔壁房里翻箱子。",
            ),
        ),
        truth = "小六欠了赌档三十两，三天内还不上就要挨打。八点掌柜下酒窖，把柜台交给了他。他从钥匙板上取下天字二号的备用钥匙，" +
            "穿过灶房，冒雨从后楼梯上了二楼，开箱拿走银票，藏进灶膛的冷灰里。慌忙回来挂钥匙时，挂错了钩子。" +
            "楼梯上的泥印、郑小姐看见的灯光、吴先生眼里那段不见人影的工夫，把这一刻钟拼了起来。",
    )

    // ---- 3. 夜车上的翡翠 -----------------------------------------------------------------------

    private fun train() = Case(
        id = "train",
        number = 3,
        title = "夜车上的翡翠",
        place = "沪宁夜车 · 头等卧铺车厢",
        time = "民国二十五年秋",
        brief = "珠宝商沈老板带着一枚翡翠观音吊坠去南京交货。夜车开到南京前，他打开首饰盒，发现吊坠变成了一块仿品。" +
            "这节车厢里只有四个包厢：1号沈老板，2号许医生，3号沈老板的秘书苏小姐，4号魔术师白先生。乘务员老孟一夜都在这节车厢值班。",
        props = listOf(
            Prop("roof", "车厢顶", LIFT, where = "车厢的顶盖（捏住往上提，看看包厢里面）"),
            Prop("jewelbox", "首饰盒", EXAMINE, gives = listOf("fake"), needs = listOf("roof"), hiddenSide = Side.BOTTOM, where = "1号包厢的首饰盒（翻过来看看吊坠底部）", look = "沈老板装翡翠吊坠的丝绒盒。"),
            Prop("teacup", "茶杯", EXAMINE, gives = listOf("tea"), needs = listOf("roof"), hiddenSide = Side.TOP, where = "1号包厢小桌上的茶杯（转过来看杯底）", look = "沈老板小桌上的一只白瓷杯。"),
            Prop("medcase", "药箱", OPEN, needs = listOf("roof"), where = "2号包厢的药箱"),
            Prop("vials", "药瓶架", EXAMINE, gives = listOf("vial"), needs = listOf("medcase"), where = "药箱里的药瓶架", look = "许医生药箱里的一排小药瓶。"),
            Prop("trenchcoat", "米色风衣", EXAMINE, gives = listOf("receipt"), needs = listOf("roof"), hiddenSide = Side.LEFT, where = "2号包厢挂着的米色风衣（转过来看看口袋）", look = "挂在包厢门后的一件风衣。"),
            Prop("bankletter", "银行的信", EXAMINE, gives = listOf("debt"), needs = listOf("roof"), where = "2号包厢枕边的信", look = "许医生枕边一封银行寄的信。"),
            Prop("telegram", "电报", EXAMINE, gives = listOf("telegram"), needs = listOf("roof"), where = "3号包厢的电报", look = "一张折过两折的电报纸。"),
            Prop("propbox", "道具箱", OPEN, gives = listOf("props"), needs = listOf("roof"), where = "4号包厢的道具箱"),
            Prop("timetable", "时刻表", EXAMINE, gives = listOf("timetable"), needs = listOf("roof"), where = "走廊墙上的时刻表", look = "走廊墙上贴的一张列车时刻表。"),
            Prop("patrol", "巡查记录", EXAMINE, gives = listOf("patrol"), needs = listOf("roof"), where = "车厢尽头乘务员室桌上的巡查记录", look = "乘务员老孟夜里写的本子。"),
        ),
        people = listOf(
            Person(
                "shen", "沈老板", "珠宝商", "五十五岁，上海老字号珠宝行的东家。这枚翡翠观音是南京一位老主顾订的。",
                suspect = false,
                questions = listOf(
                    Question("shen_night", "吊坠最后一次见到是什么时候？", "十点我拿出来给苏小姐看过，放回首饰盒，锁进公文包。十点半许医生——我们是老朋友——给我送来一杯安神茶，" +
                        "我喝了就睡得死死的，一觉到快进南京站才醒。", gives = listOf("shen_tea")),
                    Question("shen_xu", "你和许医生很熟？", "二十年的老朋友了。她开了家诊所，听说这两年不太景气，可她从没跟我开过口。"),
                ),
            ),
            Person(
                "meng", "老孟", "乘务员", "五十岁，在沪宁线上跑了二十年车，话不多。",
                questions = listOf(
                    Question("meng_night", "夜里你在做什么？", "一夜都在巡车，每个钟头走一趟，在本子上记一笔。"),
                    Question("meng_patrol", "巡查记录上写着十一点四十分有人从1号包厢出来？", "镇江停车那会儿，车厢灯调暗了。我看见一个穿米色风衣的人从1号包厢出来，进了2号。" +
                        "看身形是位女客。我还以为是客人串门，就没多问。", needs = listOf("patrol"), gives = listOf("meng_saw")),
                    Question("meng_dark", "停车的时候车厢灯为什么调暗？", "规矩。夜车停站，灯调暗，免得晃醒睡觉的客人。镇江停十分钟，就那十分钟最暗。", needs = listOf("timetable")),
                ),
            ),
            Person(
                "bai", "白先生", "魔术师", "三十五岁，在上海大世界登台变戏法，这次去南京走穴。",
                questions = listOf(
                    Question("bai_night", "夜里你在做什么？", "在餐车给大伙儿变戏法，从十一点变到十二点半，掌声可没断过。"),
                    Question("bai_props", "你道具箱里的宝石是怎么回事？", "那些是红的蓝的玻璃，变戏法用的，远看唬人。可那么像的翡翠，我可做不出来。", needs = listOf("props")),
                ),
            ),
            Person(
                "xu", "许医生", "医生", "四十岁，在上海开一家私人诊所，去南京开会。",
                questions = listOf(
                    Question("xu_night", "夜里你在做什么？", "我十一点就睡了，一直没出过包厢。"),
                    Question("xu_tea", "沈老板茶杯底有安眠药？", "……是我加的，一点安眠散。他说坐夜车总失眠，我是医生，这有什么不对？", needs = listOf("tea"), gives = listOf("xu_tea")),
                    Question("xu_debt", "银行的催款信是怎么回事？", "那是我的私事，跟这件案子没关系。", needs = listOf("debt")),
                    Question("xu_receipt", "你风衣口袋里瑞华玻璃行的收据？", "许医生沉默了很久，没有回答。", needs = listOf("receipt")),
                ),
            ),
            Person(
                "su", "苏小姐", "秘书", "二十八岁，给沈老板做了五年秘书，记性极好。",
                questions = listOf(
                    Question("su_night", "夜里你在做什么？", "十一点到十二点半，我在餐车看白先生变戏法，他变了一晚上，我也看了一晚上。", gives = listOf("su_dining")),
                    Question("su_telegram", "这封电报说你下个月要去洋行？", "是的，我要辞职了。沈老板知道，他还给我写了推荐信。", needs = listOf("telegram")),
                ),
            ),
        ),
        clues = listOf(
            e("fake", "玻璃仿品", "灯下看，吊坠里有细小的气泡，敲起来声音发脆——是玻璃。底部刻着极小的四个字：瑞华玻璃。"),
            e("tea", "杯底的白粉", "茶杯底积着一层白色细粉，闻起来微微发苦，像是安眠药。"),
            e("vial", "空着的药格", "药瓶架上「安眠散」那一格空着。标签上写：每次半钱，不可多服。"),
            e("receipt", "玻璃行的收据", "瑞华玻璃行收据：仿翠玻璃观音一枚，许女士订制，九月十二日取件。"),
            e("debt", "银行的催款信", "许氏诊所抵押借款五千元，本月底到期，逾期即收回诊所房产。"),
            e("telegram", "苏小姐的电报", "电报：苏小姐，洋行已录用，请于下月一日到职。"),
            e("props", "道具宝石", "道具箱里有一把彩色玻璃宝石，红的、蓝的、黄的，偏偏没有一颗绿色。"),
            e("timetable", "时刻表", "沪宁夜车：镇江站 23:35 到，23:45 开，停车十分钟。"),
            e("patrol", "巡查记录", "老孟的巡查记录：23:40，镇江停车，见一穿米色风衣者从1号包厢出，入2号包厢。"),
            t("shen_tea", "沈老板：一杯安神茶", "十点吊坠还在。十点半许医生送来一杯安神茶，沈老板喝完就睡得很沉，直到快进南京站。"),
            t("meng_saw", "老孟：米色风衣的女客", "镇江停车、车厢灯调暗时，老孟看见一位穿米色风衣的女客从1号包厢出来，进了2号。"),
            t("xu_tea", "许医生：茶里的安眠散", "许医生承认，在给沈老板的茶里加了安眠散。"),
            t("su_dining", "苏小姐：餐车里的戏法", "十一点到十二点半，苏小姐一直在餐车看白先生变戏法。两人互相看得见。"),
            d("drugged", "推论：沈老板是被弄睡的", "杯底的白粉，就是许医生药箱里空掉的那一格安眠散——沈老板睡得那么沉，是有人有意为之。"),
            d("swap_time", "推论：镇江停车的十分钟", "镇江停车十分钟，车厢灯最暗。23:40 有人从1号包厢出来——吊坠就是那时被换掉的。"),
            d("maker", "推论：仿品是许医生订的", "假吊坠底部的「瑞华玻璃」，正对得上许医生风衣口袋里的订制收据——仿品是她早就订做好的。"),
            d("alibi", "推论：餐车里的两个人", "白先生和苏小姐整段时间都在餐车，互相看得见，谁也没有离开去1号包厢。"),
        ),
        deductions = listOf(
            Deduction("tea", "vial", "drugged"),
            Deduction("patrol", "timetable", "swap_time"),
            Deduction("fake", "receipt", "maker"),
            Deduction("su_dining", "props", "alibi"),
        ),
        solution = Solution(
            culprit = "xu",
            motive = setOf("debt"),
            proof = setOf("maker", "drugged", "swap_time", "meng_saw"),
            wrong = mapOf(
                "meng" to "老孟一夜都在巡车、记本子，他的记录反倒写下了从1号包厢出来的人。",
                "bai" to "白先生整段时间在餐车变戏法，苏小姐和满车的客人都看着；他的道具宝石里也没有绿色的。",
                "su" to "苏小姐十一点到十二点半一直在餐车；要去洋行的电报，说明不了她要拿吊坠。",
            ),
        ),
        truth = "许医生的诊所抵押到期，还不上五千元就要关门。她知道老朋友这趟要带翡翠观音去南京，九月就在瑞华玻璃行订做了一枚仿品。" +
            "夜里她送去一杯加了安眠散的「安神茶」，等到镇江停车、车厢灯调暗的十分钟，溜进1号包厢换走了吊坠。" +
            "老孟看见的那件米色风衣，口袋里还装着玻璃行的收据。",
    )

    // ---- 4. 戏园后台的凤冠 ---------------------------------------------------------------------

    private fun opera() = Case(
        id = "opera",
        number = 4,
        title = "戏园后台的凤冠",
        place = "北平 · 春和戏园后台",
        time = "民国二十三年春，夜戏",
        brief = "春和戏园的镇班之宝，是霍班主祖上传下来的一顶点翠珍珠凤冠。今晚九点开锣前，管箱的老葛当着班主的面把它锁进了盔头箱；" +
            "十点一刻大轴《贵妃醉酒》要上场，梳头的芸姐去取，凤冠不见了。戏还没散，前后门都有人守着，凤冠一定还在后台。" +
            "今晚在后台的有：管箱的老葛、武生小马、梳头的芸姐和琴师钟先生。",
        props = listOf(
            Prop("kuitou", "盔头箱", OPEN, gives = listOf("unlocked"), where = "管箱房的红漆盔头箱"),
            Prop("crownbox", "空锦盒", EXAMINE, gives = listOf("hair"), needs = listOf("kuitou"), hiddenSide = Side.BACK, where = "盔头箱里的空锦盒（转过去看看背面的铜扣）", look = "原先装凤冠的锦盒，空了。"),
            Prop("guazi", "老葛的褂子", EXAMINE, gives = listOf("keyrosin"), hiddenSide = Side.LEFT, where = "管箱房衣钩上老葛的褂子（转过来看看口袋）", look = "管箱老葛常穿的蓝布褂子。"),
            Prop("playbill", "戏单", EXAMINE, gives = listOf("playbill"), where = "琴房墙上的戏单", look = "琴房墙上贴着的一张红纸。"),
            Prop("jinghu", "京胡", EXAMINE, gives = listOf("bow"), hiddenSide = Side.RIGHT, where = "琴房桌上的京胡（转过来看看琴弓）", look = "琴师钟先生拉的那把京胡。"),
            Prop("qinbox", "琴盒", OPEN, where = "琴房的琴盒"),
            Prop("huaigu", "一封信", EXAMINE, gives = listOf("letter"), needs = listOf("qinbox"), where = "琴盒里的一封信", look = "夹在琴盒衬布里的一封信。"),
            Prop("ledger", "账本", EXAMINE, gives = listOf("ledger"), where = "班主桌上的账本", look = "班主桌上一本蓝布面的簿子。"),
            Prop("boots", "厚底靴", EXAMINE, gives = listOf("boots"), hiddenSide = Side.BOTTOM, where = "化妆间凳子边小马的厚底靴（翻过来看看靴底）", look = "武生小马上台穿的靴子。"),
            Prop("combbox", "梳头匣", OPEN, gives = listOf("feathers"), where = "梳妆台上芸姐的梳头匣"),
        ),
        people = listOf(
            Person(
                "huo", "霍班主", "班主", "五十五岁，春和戏园的班主。凤冠是他祖上传下来的，平时锁在盔头箱里，只有唱大轴才请出来。",
                suspect = false,
                questions = listOf(
                    Question("huo_when", "凤冠最后一次见到是什么时候？", "九点开锣前，我亲眼看着老葛把凤冠放进锦盒，锁上盔头箱。十点一刻芸姐去取，箱子锁得好好的，凤冠没了。", gives = listOf("huo_seen")),
                    Question("huo_key", "盔头箱的钥匙在谁手上？", "只有一把，老葛管着。他干活嫌钥匙碍事，总把钥匙串搁在褂子口袋里，褂子就挂在管箱房的衣钩上。", gives = listOf("huo_key")),
                    Question("huo_ledger", "账本上琴师那一栏，为什么把「钟」划掉了？", "钟先生这两年手越来越不稳，下一季我另请了琴师，还没跟他说破……账本就摊在桌上，谁路过都看得见。", needs = listOf("ledger"), gives = listOf("huo_fire")),
                ),
            ),
            Person(
                "ge", "老葛", "管箱师傅", "六十岁，在春和管了三十年戏箱，盔头、戏衣、刀枪把子都归他。",
                questions = listOf(
                    Question("ge_night", "开锣以后你在做什么？", "我一直在上场门边上检场，递刀枪、搬桌椅，一刻也走不开。"),
                    Question("ge_key", "你的钥匙一直放在褂子口袋里？", "是啊，褂子挂在衣钩上。检场要跑前跑后，揣着一串钥匙叮当乱响，台上听得见。", needs = listOf("huo_key")),
                    Question("ge_rosin", "钥匙上沾着松香末，你知道是怎么回事吗？", "松香？我从来不碰那玩意儿……对了！九点二十，《三岔口》打得正热闹，我回管箱房取一对单刀，" +
                        "看见一个穿黑衣裳的人从屋里闪出去，往琴房那头去了。后台灯暗，没瞧见脸，我还当是小马下场换衣裳。", needs = listOf("keyrosin"), gives = listOf("ge_saw")),
                ),
            ),
            Person(
                "ma", "小马", "武生", "十九岁，春和的当家武生，今晚唱开锣戏《三岔口》。",
                questions = listOf(
                    Question("ma_night", "今晚你在做什么？", "九点到九点四十，我在台上唱《三岔口》，摸黑开打，一下都没下过台。满园子的人都看着呢！", gives = listOf("ma_stage")),
                    Question("ma_boots", "你靴底的松香粉是怎么回事？", "武生上台前都在靴底抹松香，不然台上打滑。后台那块松香，谁都能抠一点。", needs = listOf("boots")),
                    Question("ma_black", "老葛说九点二十看见一个穿黑衣裳的人，像你。", "九点二十？那会儿我正在台上跟刘利华摸黑过招呢！后台穿黑的又不止我一个。", needs = listOf("ge_saw")),
                ),
            ),
            Person(
                "yun", "芸姐", "梳头师傅", "三十五岁，给角儿梳头、贴片子、戴头面，后台的头饰都要经她的手。",
                questions = listOf(
                    Question("yun_night", "今晚你在做什么？", "我一直在化妆间给云老板梳头、贴片子，一步没离开。十点一刻去取凤冠，箱子一开，我就傻了眼。"),
                    Question("yun_feathers", "梳头匣里这包碎翠羽是哪来的？", "修头面用的，班主知道。凤冠上的点翠是一整片一整片贴在铜胎上的，我这些碎羽毛补不上去。", needs = listOf("feathers")),
                    Question("yun_black", "今晚后台有谁穿黑衣裳？", "小马的戏服是黑的，可他在台上。另一个就是钟先生，他一年到头穿那件黑绸长衫。" +
                        "九点多我还看见他在琴房门口转悠，《三岔口》只用锣鼓，他闲着没事。", needs = listOf("ge_saw"), gives = listOf("yun_black")),
                ),
            ),
            Person(
                "zhong", "钟先生", "琴师", "四十五岁，给春和拉了十二年京胡，平日话很少。",
                questions = listOf(
                    Question("zhong_night", "今晚你在做什么？", "《三岔口》没我的事，我一直在琴房里调弦。九点四十上场拉《拾玉镯》，一直拉到十点一刻。"),
                    Question("zhong_bow", "你的琴弓断了一绺马尾？", "拉琴的人，弓毛断几根是常有的事。", needs = listOf("bow")),
                    Question("zhong_letter", "怀古斋的这封信是怎么回事？", "钟先生把眼镜摘下来，擦了又擦，一句话也没说。", needs = listOf("letter")),
                ),
            ),
        ),
        clues = listOf(
            e("unlocked", "没撬过的箱锁", "盔头箱的铜锁完好，没有一点撬痕，是用钥匙打开的。空锦盒边上掉着一根翠蓝的羽毛。"),
            e("hair", "铜扣上的白马尾", "锦盒背面的铜扣上缠着一根细长的白马尾，比头发硬得多，还沾着黄色的松香末。"),
            e("keyrosin", "钥匙上的松香", "老葛褂子口袋里有一串钥匙。盔头箱那把铜钥匙上，沾着一点黄色的松香末。"),
            e("playbill", "今晚的戏单", "九点《三岔口》，武戏，只用锣鼓；九点四十《拾玉镯》，文戏，京胡伴奏；十点一刻大轴《贵妃醉酒》。"),
            e("bow", "断了马尾的琴弓", "钟先生京胡的琴弓上，有一绺马尾刚断，断口还很新，弓杆上沾满了松香末。"),
            e("letter", "怀古斋的信", "「钟先生台鉴：所言凤冠一事，敝号愿出大洋两千，后日午时，前门外交割。怀古斋。」"),
            e("ledger", "班主的账本", "账本上排着下一季的戏份。琴师一栏，「钟」字被一笔划掉，旁边写着「另聘」。"),
            e("boots", "靴底的松香粉", "小马的厚底靴底抹着一层松香粉。武生上台前都这么做，防止打滑。"),
            e("feathers", "一包碎翠羽", "梳头匣里有一小包碎翠羽和一小瓶胶，是修补头面用的。"),
            t("huo_seen", "霍班主：九点还在", "九点开锣前，凤冠锁进了盔头箱；十点一刻打开，凤冠没了。"),
            t("huo_key", "霍班主：钥匙在褂子里", "盔头箱只有一把钥匙。老葛把钥匙串放在褂子口袋里，褂子挂在管箱房的衣钩上。"),
            t("huo_fire", "霍班主：下一季不用钟先生", "下一季班主另请了琴师，还没跟钟先生说破；账本一直摊在桌上。"),
            t("ge_saw", "老葛：九点二十的黑衣人", "九点二十，老葛回管箱房取刀，看见一个穿黑衣裳的人从屋里出来，往琴房那头去了。"),
            t("ma_stage", "小马：一直在台上", "九点到九点四十，小马一直在台上唱《三岔口》，满园子的人都看着。"),
            t("yun_black", "芸姐：穿黑衣裳的两个人", "今晚后台穿黑的只有小马和钟先生。九点多，芸姐看见钟先生在琴房门口转悠。"),
            d("keyed", "推论：开箱的人手上有松香", "箱锁没撬，是用钥匙开的；钥匙上又沾着松香末——开箱的人从老葛的褂子里摸出了钥匙，手上带着松香。"),
            d("hair_bow", "推论：马尾来自钟先生的琴弓", "锦盒铜扣上那根沾着松香的白马尾，正是钟先生琴弓上新断的那一绺。"),
            d("free_man", "推论：九点二十谁闲着", "九点二十台上唱的是只用锣鼓的《三岔口》：小马在台上，老葛在检场，只有琴师没事可做。那个黑衣人，是当时不用上台的人。"),
            d("ma_clear", "推论：松香不是小马留的", "小马靴底有松香不假，可九点到九点四十他一直在台上，根本没机会去管箱房。"),
        ),
        deductions = listOf(
            Deduction("unlocked", "keyrosin", "keyed"),
            Deduction("hair", "bow", "hair_bow"),
            Deduction("ge_saw", "playbill", "free_man"),
            Deduction("boots", "ma_stage", "ma_clear"),
        ),
        solution = Solution(
            culprit = "zhong",
            motive = setOf("ledger", "huo_fire"),
            proof = setOf("letter", "hair_bow", "free_man", "yun_black"),
            wrong = mapOf(
                "ge" to "老葛开锣后一直在上场门检场；钥匙是被人从他褂子里摸走的，他自己倒看见了那个黑衣人。",
                "ma" to "小马九点到九点四十一直在台上唱《三岔口》，满园子的人都看着；靴底的松香是武生上台的规矩。",
                "yun" to "芸姐一直在化妆间给角儿梳头；碎翠羽是修头面用的，凤冠上的点翠是整片的，跟它们对不上。",
            ),
        ),
        truth = "钟先生偷看了班主的账本，知道下一季自己要被辞退。琉璃厂怀古斋的掌柜早就眼馋霍家的凤冠，托人捎信出价两千大洋。" +
            "九点二十，台上《三岔口》锣鼓正响，没有琴师的事。他溜进管箱房，从老葛的褂子里摸出钥匙打开盔头箱——手上的松香蹭在了钥匙上，" +
            "琴弓上断的一绺马尾缠在了锦盒的铜扣上。他把凤冠藏进琴房一只旧琴盒的夹层，九点四十若无其事地上场拉《拾玉镯》。" +
            "凤冠在夹层里找到了，那晚的《贵妃醉酒》晚开了半个钟头。",
    )
}
