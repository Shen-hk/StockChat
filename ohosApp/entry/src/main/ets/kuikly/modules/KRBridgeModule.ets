import { abilityAccessCtrl, common, Permissions } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { KRAny } from '@kuikly-open/render';
import { KuiklyRenderBaseModule } from '@kuikly-open/render';
import { KuiklyRenderCallback } from '@kuikly-open/render';
import { speechRecognizer } from '@kit.CoreSpeechKit';
import util from '@ohos.util';
import fs from '@ohos.file.fs';
import { fileUri } from '@kit.CoreFileKit';
import picker from '@ohos.file.picker';
import cameraPicker from '@ohos.multimedia.cameraPicker';
import audio from '@ohos.multimedia.audio';

interface MediaResult {
  type: string;
  source: string;
  items: Array<Record<string, string>>;
}

export class KRBridgeModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = "HRBridgeModule";
  static readonly SHOW_ALERT = "showAlert";
  static readonly CLOSE_PAGE = "closePage";
  static readonly OPEN_PAGE = "openPage";
  static readonly COPY_TO_PASTE_BOARD = "copyToPasteboard";
  static readonly TOAST = "toast";
  static readonly HAPTIC_IMPACT = "hapticImpact";
  static readonly LOG = "log";
  static readonly REPORT_DT = "reportDT";
  static readonly REPORT_REAL_TIME = "reportRealtime";
  static readonly LOCAL_SERVE_TIME = "localServeTime";
  static readonly CURRENT_TIME_STAMP = "currentTimestamp";
  static readonly DATE_FORMATTER = "dateFormatter";
  static readonly READ_ASSET_FILE = "readAssetFile";
  static readonly START_VOICE_RECORDING = "startVoiceRecording";
  static readonly STOP_VOICE_RECORDING = "stopVoiceRecording";
  static readonly CANCEL_VOICE_RECORDING = "cancelVoiceRecording";
  static readonly OPEN_COMPOSER_MEDIA_SOURCE = "openComposerMediaSource";
  static readonly REGISTER_COMPOSER_MEDIA_RESULT = "registerComposerMediaResult";
  static readonly PREPARE_AI_MEDIA = "prepareAiMedia";

  private composerMediaCallback?: KuiklyRenderCallback;
  private voiceCallback?: KuiklyRenderCallback;
  private voiceActive: boolean = false;
  private voiceCapturer?: audio.AudioCapturer;
  // ASR 会话状态：CoreSpeechKit 引擎 + 待写引擎的 PCM 攒批。
  // 引擎只接受 1280 字节（40ms@16kHz/16bit/mono）块且调用间隔须为 40ms，
  // 所以采集侧只攒 buffer，由 voiceAsrTimer 定速 flush。
  private asrEngine?: speechRecognizer.SpeechRecognitionEngine;
  private asrSessionId: string = "";
  private voiceStopping: boolean = false;
  private voicePendingText: string = "";
  private voiceAsrBuffer: Uint8Array = new Uint8Array(0);
  private voiceAsrTimer?: number;

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback): KRAny {
    switch (method) {
      case KRBridgeModule.SHOW_ALERT: {
        this.showAlert(params, callback);
        break;
      }
      case KRBridgeModule.CLOSE_PAGE: {
        this.closePage();
        break;
      }
      case KRBridgeModule.OPEN_PAGE: {
        this.openPage();
        break;
      }
      case KRBridgeModule.COPY_TO_PASTE_BOARD: {
        this.copyToPasteboard();
        break;
      }
      case KRBridgeModule.TOAST: {
        this.toast();
        break;
      }
      case KRBridgeModule.HAPTIC_IMPACT: {
        this.hapticImpact();
        break;
      }
      case KRBridgeModule.LOG: {
        this.log(params);
        break;
      }
      case KRBridgeModule.REPORT_DT: {
        this.reportDT(params);
        break;
      }
      case KRBridgeModule.REPORT_REAL_TIME: {
        this.reportRealtime(params);
        break;
      }
      case KRBridgeModule.LOCAL_SERVE_TIME: {
        this.localServeTime(params, callback);
        break;
      }
      case KRBridgeModule.CURRENT_TIME_STAMP: {
        return this.currentTimestamp();
      }
      case KRBridgeModule.DATE_FORMATTER: {
        return this.dateFormatter(params);
      }
      case KRBridgeModule.READ_ASSET_FILE: {
        this.readAssetFile(params, callback);
        break;
      }
      case KRBridgeModule.START_VOICE_RECORDING: {
        this.startVoiceRecording(callback);
        break;
      }
      case KRBridgeModule.STOP_VOICE_RECORDING: {
        return this.stopVoiceRecording();
      }
      case KRBridgeModule.CANCEL_VOICE_RECORDING: {
        this.cancelVoiceRecording();
        break;
      }
      case KRBridgeModule.OPEN_COMPOSER_MEDIA_SOURCE: {
        this.openComposerMediaSource(params, callback);
        break;
      }
      case KRBridgeModule.REGISTER_COMPOSER_MEDIA_RESULT: {
        this.composerMediaCallback = callback;
        break;
      }
      case KRBridgeModule.PREPARE_AI_MEDIA: {
        this.prepareAiMedia(params, callback);
        break;
      }
      default:
        break;
    }
    return null;
  }

  private showAlert(params: KRAny, callback: KuiklyRenderCallback) {
  }

  // TODO close page
  private closePage() {
  }

  // TODO open new page
  private openPage() {
  }

  // TODO copy to paste board
  private copyToPasteboard() {
  }

  // TODO open toast
  private toast() {
  }

  private hapticImpact() {
  }

  private startVoiceRecording(callback: KuiklyRenderCallback) {
    this.voiceCallback = callback;
    this.voiceActive = true;
    this.voiceStopping = false;
    this.voicePendingText = "";
    this.voiceAsrBuffer = new Uint8Array(0);
    this.asrSessionId = `voice_${Date.now()}`;
    callback({ "type": "ready" });
    // user_grant 权限必须运行时申请，否则 createAudioCapturer 直接 201。
    let atManager = abilityAccessCtrl.createAtManager();
    let context = getContext(this) as common.UIAbilityContext;
    atManager.requestPermissionsFromUser(context, ['ohos.permission.MICROPHONE'] as Array<Permissions>)
      .then((result): void => {
        if (!this.voiceActive) return;
        let granted = result.authResults.indexOf(0) >= 0;
        if (!granted) {
          this.voiceActive = false;
          this.voiceCallback?.({ "type": "error", "error": "PERMISSION_DENIED", "message": "需要麦克风权限" });
          return;
        }
        this.createAsrEngine();
      })
      .catch((error: BusinessError): void => {
        if (!this.voiceActive) return;
        this.voiceActive = false;
        this.voiceCallback?.({ "type": "error", "error": "PERMISSION_DENIED", "message": error.message });
      });
  }

  private createAsrEngine(): void {
    let extraParams: Record<string, Object> = { 'locate': 'CN', 'recognizerMode': 'short' };
    let initParamsInfo: speechRecognizer.CreateEngineParams = {
      language: 'zh-CN',
      online: 1,
      extraParams: extraParams
    };
    speechRecognizer.createEngine(initParamsInfo, (err: BusinessError, engine: speechRecognizer.SpeechRecognitionEngine): void => {
      if (err) {
        console.error(`asr createEngine failed: ${err.code}, ${err.message}`);
        if (this.voiceActive) {
          this.voiceActive = false;
          this.voiceCallback?.({ "type": "error", "error": "UNAVAILABLE", "message": "当前设备暂不支持语音识别" });
        }
        return;
      }
      if (!this.voiceActive) {
        // 期间用户已取消，不能泄漏刚创建的引擎。
        try {
          engine.shutdown();
        } catch (error) {
          console.error(`asr shutdown failed: ${JSON.stringify(error)}`);
        }
        return;
      }
      this.asrEngine = engine;
      engine.setListener({
        onStart: (sessionId: string, eventMessage: string): void => {
        },
        onEvent: (sessionId: string, eventCode: number, eventMessage: string): void => {
        },
        onResult: (sessionId: string, result: speechRecognizer.SpeechRecognitionResult): void => {
          // 中间结果与最终结果都更新，松手时取最新一份。
          this.voicePendingText = result.result;
          this.voiceCallback?.({ "type": "partial", "text": result.result });
        },
        onComplete: (sessionId: string, eventMessage: string): void => {
          if (this.voiceStopping) {
            this.deliverVoiceTranscript();
          }
        },
        onError: (sessionId: string, errorCode: number, errorMessage: string): void => {
          if (this.voiceStopping) {
            // finish 之后引擎报错时交付已识别文本，好过空结果。
            this.deliverVoiceTranscript();
            return;
          }
          this.voiceActive = false;
          this.stopVoiceAsrTimer();
          this.releaseVoiceCapturer();
          this.shutdownAsrEngine();
          this.voiceCallback?.({ "type": "error", "error": this.mapVoiceErrorCode(errorCode), "message": errorMessage });
        }
      });
      this.startAsrListening();
    });
  }

  private startAsrListening(): void {
    let engine = this.asrEngine;
    if (engine == undefined) return;
    let recognizerParams: speechRecognizer.StartParams = {
      sessionId: this.asrSessionId,
      audioInfo: { audioType: 'pcm', sampleRate: 16000, soundChannel: 1, sampleBit: 16 }
    };
    try {
      engine.startListening(recognizerParams);
    } catch (error) {
      console.error(`asr startListening failed: ${JSON.stringify(error)}`);
      this.voiceActive = false;
      this.shutdownAsrEngine();
      this.voiceCallback?.({ "type": "error", "error": "MIC_OCCUPIED", "message": "无法启动语音识别" });
      return;
    }
    this.createVoiceCapturer();
    // 引擎写入节奏约束：1280B/40ms 与 16kHz 采集速率正好收支平衡。
    this.voiceAsrTimer = setInterval((): void => this.flushAsrAudio(), 40);
  }

  private createVoiceCapturer(): void {
    let options: audio.AudioCapturerOptions = {
      streamInfo: {
        samplingRate: audio.AudioSamplingRate.SAMPLE_RATE_16000,
        channels: audio.AudioChannel.CHANNEL_1,
        sampleFormat: audio.AudioSampleFormat.SAMPLE_FORMAT_S16LE,
        encodingType: audio.AudioEncodingType.ENCODING_TYPE_RAW
      },
      capturerInfo: {
        source: audio.SourceType.SOURCE_TYPE_VOICE_RECOGNITION,
        capturerFlags: 0
      }
    };
    audio.createAudioCapturer(options).then((capturer: audio.AudioCapturer): void => {
      if (!this.voiceActive) {
        capturer.release();
        return;
      }
      this.voiceCapturer = capturer;
      capturer.on('readData', (buffer: ArrayBuffer): void => {
        if (!this.voiceActive || buffer.byteLength < 2) return;
        this.appendVoicePcm(buffer);
      });
      capturer.start().catch((): void => this.voiceCallback?.({
        "type": "error", "error": "MIC_OCCUPIED", "message": "无法启动麦克风"
      }));
    }).catch((error: BusinessError): void => {
      this.voiceActive = false;
      this.voiceCallback?.({ "type": "error", "error": "PERMISSION_DENIED", "message": error.message });
    });
  }

  private appendVoicePcm(buffer: ArrayBuffer): void {
    let incoming = new Uint8Array(buffer);
    let merged = new Uint8Array(this.voiceAsrBuffer.length + incoming.length);
    merged.set(this.voiceAsrBuffer, 0);
    merged.set(incoming, this.voiceAsrBuffer.length);
    this.voiceAsrBuffer = merged;
  }

  private flushAsrAudio(): void {
    if (!this.voiceActive) return;
    let engine = this.asrEngine;
    if (engine == undefined || this.voiceAsrBuffer.length < 1280) return;
    let chunk = this.voiceAsrBuffer.slice(0, 1280);
    this.voiceAsrBuffer = this.voiceAsrBuffer.slice(1280);
    // 写入引擎的同一块数据顺手算 RMS，声波与识别天然同步。
    let sum: number = 0;
    for (let offset = 0; offset + 1 < 1280; offset += 2) {
      let raw = chunk[offset] | (chunk[offset + 1] << 8);
      if (raw >= 32768) raw -= 65536;
      let sample = raw / 32768.0;
      sum += sample * sample;
    }
    let rms = Math.sqrt(sum / 640);
    this.voiceCallback?.({ "type": "amplitude", "rms": rms.toString() });
    try {
      engine.writeAudio(this.asrSessionId, chunk);
    } catch (error) {
      console.error(`asr writeAudio failed: ${JSON.stringify(error)}`);
    }
  }

  private stopVoiceRecording(): string {
    this.voiceActive = false;
    this.voiceStopping = true;
    this.stopVoiceAsrTimer();
    this.releaseVoiceCapturer();
    let engine = this.asrEngine;
    if (engine != undefined) {
      try {
        engine.finish(this.asrSessionId);
      } catch (error) {
        console.error(`asr finish failed: ${JSON.stringify(error)}`);
        // 会话可能已被引擎自动收尾，直接交付已有文本。
        this.deliverVoiceTranscript();
      }
    } else {
      this.deliverVoiceTranscript();
    }
    // 引擎回调丢失的兜底：3s 后仍未交付就交付已有文本。
    setTimeout((): void => {
      if (this.voiceStopping) {
        this.deliverVoiceTranscript();
      }
    }, 3000);
    return JSON.stringify({ "path": "" });
  }

  private cancelVoiceRecording() {
    this.voiceActive = false;
    this.voiceStopping = false;
    this.voicePendingText = "";
    this.voiceAsrBuffer = new Uint8Array(0);
    this.stopVoiceAsrTimer();
    this.releaseVoiceCapturer();
    let engine = this.asrEngine;
    this.asrEngine = undefined;
    if (engine != undefined) {
      try {
        engine.cancel(this.asrSessionId);
      } catch (error) {
        console.error(`asr cancel failed: ${JSON.stringify(error)}`);
      }
      try {
        engine.shutdown();
      } catch (error) {
        console.error(`asr shutdown failed: ${JSON.stringify(error)}`);
      }
    }
    this.voiceCallback = undefined;
  }

  private deliverVoiceTranscript(): void {
    let callback = this.voiceCallback;
    let text = this.voicePendingText;
    this.voiceCallback = undefined;
    this.voiceActive = false;
    this.voiceStopping = false;
    this.voicePendingText = "";
    this.stopVoiceAsrTimer();
    this.releaseVoiceCapturer();
    this.shutdownAsrEngine();
    callback?.({ "type": "transcript", "text": text });
  }

  private shutdownAsrEngine(): void {
    let engine = this.asrEngine;
    this.asrEngine = undefined;
    if (engine != undefined) {
      try {
        engine.shutdown();
      } catch (error) {
        console.error(`asr shutdown failed: ${JSON.stringify(error)}`);
      }
    }
  }

  private stopVoiceAsrTimer(): void {
    if (this.voiceAsrTimer != undefined) {
      clearInterval(this.voiceAsrTimer);
      this.voiceAsrTimer = undefined;
    }
  }

  private releaseVoiceCapturer(): void {
    let capturer = this.voiceCapturer;
    this.voiceCapturer = undefined;
    if (capturer != undefined) {
      capturer.stop().then((): Promise<void> => capturer!.release()).catch((): void => undefined);
    }
  }

  private mapVoiceErrorCode(code: number): string {
    if (code == 201) return "PERMISSION_DENIED";
    if (code == 1002200006) return "MIC_OCCUPIED";
    return "UNAVAILABLE";
  }

  private openComposerMediaSource(params: KRAny, callback: KuiklyRenderCallback) {
    let records = JSON.parse(params as string) as Record<string, string>;
    let source = records["source"];
    let deliver = (items: Array<Record<string, string>>): void => {
      let result: MediaResult = { "type": "ok", "source": source, "items": items };
      this.composerMediaCallback?.(result);
      callback(result);
    };
    let cancel = (): void => callback({ "type": "cancel", "source": source });
    try {
      if (source == "photo") {
        let viewPicker = new picker.PhotoViewPicker();
        viewPicker.select({
          MIMEType: picker.PhotoViewMIMETypes.IMAGE_TYPE,
          maxSelectNumber: 9
        }).then((result): void => {
          let items: Array<Record<string, string>> = [];
          result.photoUris.forEach((uri: string) => items.push({
            "kind": "image", "path": uri, "name": this.fileName(uri)
          }));
          deliver(items);
        }).catch((): void => cancel());
      } else if (source == "camera") {
        let context = getContext(this) as common.UIAbilityContext;
        cameraPicker.pick(context, [cameraPicker.PickerMediaType.PHOTO], new cameraPicker.PickerProfile()).then((result): void => {
          if (result.resultUri.length > 0) {
            deliver([{ "kind": "image", "path": result.resultUri, "name": this.fileName(result.resultUri) }]);
          } else {
            cancel();
          }
        }).catch((): void => cancel());
      } else {
        let documentPicker = new picker.DocumentViewPicker();
        documentPicker.select().then((uris: Array<string>): void => {
          let items: Array<Record<string, string>> = [];
          uris.forEach((uri: string) => items.push({
            "kind": "file", "path": uri, "name": this.fileName(uri)
          }));
          deliver(items);
        }).catch((): void => cancel());
      }
    } catch (error) {
      console.error(`media picker failed: ${JSON.stringify(error)}`);
      callback({ "type": "error", "source": source });
    }
  }

  private prepareAiMedia(params: KRAny, callback: KuiklyRenderCallback) {
    let records = JSON.parse(params as string) as Record<string, Array<Record<string, string>>>;
    let input = records["items"] || [];
    let output: Array<Record<string, string>> = [];
    input.forEach((item: Record<string, string>) => {
      let path = item["path"] || "";
      if (path.length == 0) return;
      let name = item["name"] || this.fileName(path);
      let kind = item["kind"] || "file";
      let prepared: Record<string, string> = { "name": name, "kind": kind };
      let bytes = this.readFileBytes(path);
      if (bytes != null && kind == "image") {
        let encoded = new util.Base64Helper().encodeSync(bytes);
        let base64 = new util.TextDecoder('utf-8').decodeToString(encoded);
        prepared["imageDataUrl"] = `data:${this.mimeType(name)};base64,${base64}`;
      } else if (bytes != null && this.isTextFile(name)) {
        prepared["documentText"] = new util.TextDecoder('utf-8').decodeToString(bytes);
      }
      output.push(prepared);
    });
    callback({ "items": output });
  }

  private readFileBytes(path: string): Uint8Array | null {
    try {
      let file = fs.openSync(path, fs.OpenMode.READ_ONLY);
      let stat = fs.statSync(file.fd);
      let buffer = new ArrayBuffer(stat.size);
      let length = fs.readSync(file.fd, buffer);
      fs.closeSync(file);
      return new Uint8Array(buffer, 0, length);
    } catch (error) {
      console.error(`media read failed: ${JSON.stringify(error)}`);
      return null;
    }
  }

  private isTextFile(name: string): boolean {
    let lower = name.toLowerCase();
    return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv") || lower.endsWith(".json");
  }

  private mimeType(name: string): string {
    let lower = name.toLowerCase();
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".webp")) return "image/webp";
    return "image/jpeg";
  }

  private fileName(uri: string): string {
    let clean = uri.split("?")[0];
    let slash = clean.lastIndexOf("/");
    return slash >= 0 ? clean.substring(slash + 1) : "附件";
  }

  private log(params: KRAny) {
    if (params == null) {
      return
    }
    console.log(`KuiklyRender ${JSON.stringify(params)}`,)
  }

  private reportDT(params: KRAny) {
  }

  private reportRealtime(params: KRAny) {
  }

  private localServeTime(params: KRAny, callback: KuiklyRenderCallback) {
    callback({ "time": Date.now() / 1000.0 });
  }

  private currentTimestamp(): string {
    return Date.now().toString()
  }

  // TODO format date
  private dateFormatter(params: KRAny): string {
    console.log(`========== ${params}`)
    return "";
  }

  private readAssetFile(params: KRAny, callback: KuiklyRenderCallback) {
    // let startTimeMills = Date.now();
    let records = JSON.parse(params as string) as Record<string, string>;
    let assetPath = records["assetPath"];

    let context = getContext(this) as common.UIAbilityContext;
    /*
    context.resourceManager.getRawFileContent(assetPath, (error: BusinessError, value: Uint8Array) => {
      let textDecoder: util.TextDecoder = util.TextDecoder.create();
      let res: string = textDecoder.decodeWithStream(value);
      callback({
        "result": JSON.parse(res)
      });
    });
    */
    let resDir = context.resourceDir
    let path = resDir + '/' + assetPath
    let assetURI = fileUri.getUriFromPath(path)
    const file = fs.openSync(assetURI, fs.OpenMode.READ_ONLY);
    const stat = fs.statSync(file.fd);
    const arrayBuffer = new ArrayBuffer(stat.size);
    fs.read(file.fd, arrayBuffer, (err: BusinessError, readLen: number) => {
      if (err) {
        console.error("read failed with error message: " + err.message + ", error code: " + err.code);
      } else {
        let buf = new Uint8Array(arrayBuffer, 0, readLen);
        const textDecoder = new util.TextDecoder('utf-8');
        const res = textDecoder.decodeWithStream(new Uint8Array(buf));
        callback({
          'result': JSON.parse(res)
        });
      }
      fs.closeSync(file);
    });
  }
}
