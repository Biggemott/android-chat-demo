package com.biggemott.chatdemo.ui.chat

import java.util.Locale

internal fun scriptedRepliesFor(locale: Locale): List<String> = if (locale.language == "ar") arabicReplies else scriptedReplies

internal val scriptedReplies =
    listOf(
        "I'm at the bottom of the lighthouse steps. I found a bench out of the wind.",
        "Open the foldout to the harbor page. There's a dotted line behind the old wall.",
        "The first bend is just past the keeper's garden.",
        "Look for a blue gate. The path goes around it on the seaward side.",
        "I can see the harbor from here, but the atlas makes it look twice as wide.",
        "That little square on the map must be the old signal hut.",
        "The roof is gone, but the stone steps are still there.",
        "Let's stop by the wall for tea. I brought two cups.",
        "There's a date carved into the stone. Can you make out the last digit?",
        "I think the lower path used to join the slipway before they built the new quay.",
        "There's a brass marker beside the rail. It lines up with the lighthouse window.",
        "From here you can see why they drew the channel curving so sharply.",
        "The fishing boats are coming in. Let's give them room on the ramp.",
        "There's a sheltered spot just past the net shed.",
        "The harbor board has a photo of the old pier with the same crooked lamp.",
        "The tide is low enough to see the bottom step in the photo.",
        "Let's pencil the new quay onto a slip of paper and tuck it into the atlas.",
        "We could come back at high tide and compare the two routes.",
        "I marked the bench and the blue gate so we can find them again.",
        "The light's coming on. We can follow the road back from here.",
        "I'll bring the sketch to the library next time we borrow the atlas.",
    )

private val arabicReplies =
    listOf(
        "أنا عند أسفل درج المنارة. وجدت مقعدًا في مكان محمي من الريح.",
        "افتح صفحة الميناء المطوية في الأطلس. هناك خط منقّط خلف السور القديم.",
        "أول منعطف بعد حديقة حارس المنارة مباشرةً.",
        "ابحث عن بوابة زرقاء. يلتف الطريق حولها من جهة البحر.",
        "أرى الميناء من هنا، لكنه يبدو في الأطلس أعرض بمرّتين.",
        "لا بد أن ذلك المربّع الصغير على الخريطة هو كوخ الإشارات القديم.",
        "السقف اختفى، لكن الدرج الحجري لا يزال في مكانه.",
        "لنسترح عند السور ونشرب الشاي. أحضرت كوبين.",
        "هناك تاريخ محفور على الحجر. هل تستطيع تمييز الرقم الأخير؟",
        "أظن أن الطريق السفلي كان يصل إلى منحدر القوارب قبل بناء الرصيف الجديد.",
        "بجوار الدرابزين علامة نحاسية على استقامة نافذة المنارة.",
        "من هنا يتّضح لماذا رسموا الممرّ الملاحي بهذا الانحناء الحاد.",
        "قوارب الصيد عائدة إلى الميناء. لنفسح لها المجال عند المنحدر.",
        "هناك مكان محمي من الريح بعد مخزن الشباك بقليل.",
        "على لوحة الميناء صورة للرصيف القديم يظهر فيها مصباحه المائل.",
        "انحسر الماء حتى ظهرت الدرجة السفلى التي في الصورة.",
        "لنرسم الرصيف الجديد بقلم الرصاص على قصاصة ونضعها في الأطلس.",
        "يمكننا العودة وقت المدّ ومقارنة الطريقين.",
        "وضعت علامتين على الخريطة عند المقعد والبوابة الزرقاء لنجدهما مجددًا.",
        "بدأ ضوء المنارة يسطع. يمكننا العودة من هنا عبر الطريق.",
        "سأحضر الرسم معي إلى المكتبة في المرّة القادمة التي نستعير فيها الأطلس.",
    )
