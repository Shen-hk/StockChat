#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""双周进度汇报 PDF 排版脚本 (reportlab + 阿里巴巴普惠体)"""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib.colors import HexColor
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_JUSTIFY
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (BaseDocTemplate, Frame, PageTemplate, Paragraph,
                                Spacer, HRFlowable, KeepTogether)

FONTS = "/Library/Fonts"
pdfmetrics.registerFont(TTFont("PHTR", f"{FONTS}/AlibabaPuHuiTi-3-55-Regular.ttf"))
pdfmetrics.registerFont(TTFont("PHTM", f"{FONTS}/AlibabaPuHuiTi-3-65-Medium.ttf"))
pdfmetrics.registerFont(TTFont("PHTSB", f"{FONTS}/AlibabaPuHuiTi-3-75-SemiBold.ttf"))
pdfmetrics.registerFont(TTFont("PHTBD", f"{FONTS}/AlibabaPuHuiTi-3-85-Bold.ttf"))

INK    = HexColor("#1F2733")   # 正文
ACCENT = HexColor("#2456C7")   # 主题蓝
MUTED  = HexColor("#6B7686")   # 次要
LINE   = HexColor("#D8DEE8")
CODEBG = HexColor("#F2F5FA")

S = {}
S["title"]   = ParagraphStyle("title", fontName="PHTBD", fontSize=20, leading=28,
                              textColor=INK, spaceAfter=2*mm)
S["subtitle"]= ParagraphStyle("subtitle", fontName="PHTR", fontSize=10.5, leading=17,
                              textColor=MUTED, spaceAfter=4*mm)
S["h1"]      = ParagraphStyle("h1", fontName="PHTSB", fontSize=14, leading=20,
                              textColor=ACCENT, spaceBefore=6*mm, spaceAfter=2.5*mm)
S["h2"]      = ParagraphStyle("h2", fontName="PHTSB", fontSize=11.5, leading=17,
                              textColor=INK, spaceBefore=3.5*mm, spaceAfter=1.8*mm)
S["body"]    = ParagraphStyle("body", fontName="PHTR", fontSize=10.5, leading=18,
                              textColor=INK, alignment=TA_LEFT, spaceAfter=1.5*mm)
S["li"]      = ParagraphStyle("li", parent=S["body"], leftIndent=6*mm, bulletIndent=1.5*mm,
                              spaceAfter=1.2*mm)
S["li2"]     = ParagraphStyle("li2", parent=S["body"], leftIndent=12*mm, bulletIndent=7.5*mm,
                              fontSize=10, leading=16.5, spaceAfter=1*mm, textColor=INK)
S["code"]    = ParagraphStyle("code", parent=S["body"], fontName="PHTM", backColor=CODEBG,
                              borderPadding=(3,5,3,5), spaceBefore=1*mm, spaceAfter=2*mm)

def bullet(txt, style="li", sym="•"):
    return Paragraph(txt, S[style], bulletText=sym)

story = []
story.append(Paragraph("双周进度汇报 · Task 2", S["title"]))
story.append(Paragraph("李老师，这是双周的进度汇报，同时也借机会和您探讨几个问题。", S["subtitle"]))
story.append(HRFlowable(width="100%", thickness=0.8, color=LINE, spaceAfter=2*mm))

# ---- 一、当前已完成 ----
story.append(Paragraph("一、当前已完成", S["h1"]))
story.append(Paragraph("功能", S["h2"]))
story += [
    bullet("聊天闭环：流式输出、历史记录、语音输入、新建欢迎语"),
    bullet("市场行情、自选股、风险预警、知识术语表等页面的内容搭建"),
    bullet("卡片混排体系构建"),
]
story.append(Paragraph("交互", S["h2"]))
story += [
    bullet("灵动岛、拖拽点按股票实体与输入框三者形成“行情预览 → 拖拽对比详情 → 跳转页面”等交互形式"),
    bullet("@ 联想和 / 指令实现在输入框上的交互形式"),
]
story.append(Paragraph("动效", S["h2"]))
story += [
    bullet("欢迎页：轮循打字、引导语的阶梯入场、tab 切换的转场动画"),
    bullet("抽屉反馈、灵动岛与胶囊协同出入场动画"),
    bullet("图表数据的入场与更新动画"),
]

# ---- 二、未来规划 ----
story.append(Paragraph("二、未来规划", S["h1"]))
plan = [
    "卡片堆叠轮转交互",
    "多级别卡片的入场动画",
    "风险预报与推演功能的开发",
    "输出文本的海报式设计",
    "对二级页面优化设计——所见即可交互，手势即意图",
]
for i, t in enumerate(plan, 1):
    story.append(bullet(f"{i}. {t}", sym=""))

# ---- 三、问题 ----
story.append(Paragraph("三、开发过程中遇到的问题", S["h1"]))
story.append(Paragraph("动效静默失效 —— Kuikly 声明式动画冻结", S["h2"]))
story.append(Paragraph("代码不报错、没有日志，但动画不播或降级成瞬间跳变。逐个排查后复现出这几条规律：", S["body"]))
story.append(bullet("<b>animate() 的驱动 key 是隐式的</b>：它绑定的是 attr 块内“最后读取的那个 observable”，"
                    "而不是显式参数。实测在 <font name='PHTM'>attr {}</font> 里如果 animate() 之前读了别的 observable，"
                    "驱动 key 就会被抢走，目标动画不生效。"))
story.append(bullet("<b>注册滞后一个变更周期</b>：看实现是 beginApplyAttrProperty() 先消费上一个周期的注册"
                    "（AnimationState next→cur），然后重跑 attr 闭包、本周期的 animate() 只进 nextAnimations。"
                    "也就是说“这次变更播放的动画”其实是“上个周期注册的那个”。这带来几个反直觉的推论："))
story += [
    bullet("每个变更周期都必须无条件注册动画（否则下次变更没动画可播）；", "li2", "–"),
    bullet("注册一个 linear(0f) 的 reset 动画会被下一次变更消费掉，导致入场降级为跳变；", "li2", "–"),
    bullet("而同值写入因为 ObservableProperties.setValue 早退不触发通知，也没法用重写的方式冲掉错误的残留注册。", "li2", "–"),
]
story.append(bullet("<b>响应式作用域只有 attr / event / vif / vfor / vbind 闭包</b>：在普通 builder 闭包"
                    "（包括 Scroller 子项）里读 observable，读到的是初始快照且不建立依赖，state 变了界面也不动。"))
story.append(bullet("<b>animate(animation, value) 的第二参数 value: Any 在实现里完全没被使用，是死参数</b>"
                    "——而官方文档 animation-declarative.md 却把 value 描述为“需传入一个控制动画状态的响应式变量”。"
                    "也就是说文档宣称显式 driver、实现却是隐式 last-read 绑定，文档与实现感觉不太一样。"))

# ---- 四、想请教的 ----
story.append(Paragraph("四、想请教的", S["h1"]))
story.append(Paragraph("关于动画机制", S["h2"]))
story += [
    bullet("第 1、2 点是有意为之的设计权衡吗？如果是权衡，想知道当时的考量，方便我们在业务侧正确规避。"),
    bullet("我觉得可以加上显式 driver 参数、在文档写明滞后语义（官方文档写的并不详细）。"),
]
story.append(Paragraph("关于开发方法", S["h2"]))
story += [
    bullet("在 vibecoding 的时候，多种 AI 同时开发一个项目，设计的架构可能不被遵循。"
           "想问下您是偏向整体功能开发完整后再专门架构优化，还是开发过程中就强约束这些？"
           "如果是后者，想知道这方面是怎么做的？"),
    bullet("添加动画会给用户良好的交互体验，但大量动画又会造成性能问题。想知道这两方面的取舍您是怎么看待的？"),
]

# ---- 页面模板与页脚 ----
def on_page(canvas, doc):
    canvas.saveState()
    # 显式白色页面背景
    canvas.setFillColor(HexColor("#FFFFFF"))
    canvas.rect(0, 0, A4[0], A4[1], stroke=0, fill=1)
    canvas.setStrokeColor(LINE); canvas.setLineWidth(0.6)
    canvas.line(20*mm, 14*mm, A4[0]-20*mm, 14*mm)
    canvas.setFont("PHTR", 8); canvas.setFillColor(MUTED)
    canvas.drawString(20*mm, 9.5*mm, "双周进度汇报 · Task 2")
    canvas.drawRightString(A4[0]-20*mm, 9.5*mm, f"第 {doc.page} 页")
    canvas.restoreState()

doc = BaseDocTemplate("/Users/shencaiyan/StudioProjects/StockChat/outputs/双周进度汇报-Task2.pdf",
                      pagesize=A4, leftMargin=20*mm, rightMargin=20*mm,
                      topMargin=18*mm, bottomMargin=20*mm,
                      title="双周进度汇报 · Task 2")
frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="main")
doc.addPageTemplates([PageTemplate(id="page", frames=[frame], onPage=on_page)])
doc.build(story)
print("PDF done")
