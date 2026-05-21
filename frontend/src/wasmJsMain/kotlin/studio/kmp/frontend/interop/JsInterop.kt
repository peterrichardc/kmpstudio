package studio.kmp.frontend.interop

// ── Monaco ───────────────────────────────────────────────────────────────────
@JsName("kmpCreateEditorContainer")
external fun createEditorContainer(id: String, x: Int, y: Int, w: Int, h: Int)

@JsName("kmpUpdateEditorContainer")
external fun updateEditorContainer(id: String, x: Int, y: Int, w: Int, h: Int)

@JsName("kmpMonacoInit")
external fun monacoInit(id: String, value: String, language: String)

@JsName("kmpMonacoGetValue")
external fun monacoGetValue(id: String): String

@JsName("kmpMonacoSetValue")
external fun monacoSetValue(id: String, value: String)

@JsName("kmpMonacoGetSelection")
external fun monacoGetSelection(id: String): String

@JsName("kmpMonacoApplyDiff")
external fun monacoApplyDiff(id: String, hunksJson: String)

@JsName("kmpMonacoDispose")
external fun monacoDispose(id: String)

@JsName("kmpMonacoGoToLine")
external fun monacoGoToLine(id: String, line: Int)

@JsName("kmpMonacoSetMarkers")
external fun monacoSetMarkers(id: String, markersJson: String)

@JsName("kmpSetEditorVisible")
external fun setEditorVisible(id: String, visible: Boolean)

@JsName("kmpShowDiffPreview")
external fun showDiffPreview(fileName: String, original: String, modified: String, current: Int, total: Int)

@JsName("kmpHideDiffPreview")
external fun hideDiffPreview()

@JsName("kmpPopDiffResult")
external fun popDiffResult(): String?

// ── WebSocket (polling bridge) ────────────────────────────────────────────────
@JsName("kmpWsConnect")
external fun wsConnect(url: String)

@JsName("kmpWsSend")
external fun wsSend(data: String)

@JsName("kmpWsClose")
external fun wsClose()

@JsName("kmpWsGetState")
external fun wsGetState(): String

@JsName("kmpWsPopMessage")
external fun wsPopMessage(): String?

// ── File-system REST bridge ───────────────────────────────────────────────────
@JsName("kmpFsListDir")
external fun fsListDir(baseUrl: String, path: String)

@JsName("kmpFsGetListResult")
external fun fsGetListResult(): String?

@JsName("kmpFsFetchHome")
external fun fsFetchHome(baseUrl: String)

@JsName("kmpFsPopHome")
external fun fsPopHome(): String?

// ── Time ──────────────────────────────────────────────────────────────────────
@JsName("kmpGetTimeString")
external fun getTimeString(): String

// ── Debug ─────────────────────────────────────────────────────────────────────
@JsName("kmpLog")
external fun consoleLog(msg: String)

// ── Agent health check ────────────────────────────────────────────────────────
@JsName("kmpCheckAgentHealth")
external fun checkAgentHealth(url: String)

@JsName("kmpGetAgentHealth")
external fun getAgentHealth(): Boolean

// ── AI key test ───────────────────────────────────────────────────────────────
@JsName("kmpTestAiKey")
external fun testAiKey(agentBaseUrl: String, provider: String, key: String, customBaseUrl: String)

@JsName("kmpGetAiTestResult")
external fun getAiTestResult(): String?

// ── Clipboard ─────────────────────────────────────────────────────────────────
@JsName("kmpCopyToClipboard")
external fun copyToClipboard(text: String)

// ── Video ─────────────────────────────────────────────────────────────────────
@JsName("kmpVideoInit")
external fun videoInit(canvasId: String)

@JsName("kmpVideoConnect")
external fun videoConnect(url: String, canvasId: String)

@JsName("kmpVideoDisconnect")
external fun videoDisconnect()
