import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { KRAny } from '@kuikly-open/render';
import { KuiklyRenderBaseModule } from '@kuikly-open/render';
import { KuiklyRenderCallback } from '@kuikly-open/render';
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
    callback({ "type": "ready" });
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
        let view = new DataView(buffer);
        let sum: number = 0;
        let samples: number = 0;
        for (let offset = 0; offset + 1 < buffer.byteLength; offset += 2) {
          let sample = view.getInt16(offset, true) / 32768.0;
          sum += sample * sample;
          samples += 1;
        }
        let rms = samples > 0 ? Math.sqrt(sum / samples) : 0;
        this.voiceCallback?.({ "type": "amplitude", "rms": rms.toString() });
      });
      capturer.start().catch((): void => this.voiceCallback?.({
        "type": "error", "error": "MIC_OCCUPIED", "message": "无法启动麦克风"
      }));
    }).catch((error: BusinessError): void => {
      this.voiceActive = false;
      this.voiceCallback?.({ "type": "error", "error": "PERMISSION_DENIED", "message": error.message });
    });
  }

  private stopVoiceRecording(): string {
    this.voiceActive = false;
    let capturer = this.voiceCapturer;
    this.voiceCapturer = undefined;
    if (capturer != undefined) {
      capturer.stop().then((): Promise<void> => capturer!.release()).catch((): void => undefined);
    }
    this.voiceCallback?.({ "type": "transcript", "text": "" });
    this.voiceCallback = undefined;
    return JSON.stringify({ "path": "" });
  }

  private cancelVoiceRecording() {
    this.voiceActive = false;
    let capturer = this.voiceCapturer;
    this.voiceCapturer = undefined;
    if (capturer != undefined) {
      capturer.stop().then((): Promise<void> => capturer!.release()).catch((): void => undefined);
    }
    this.voiceCallback = undefined;
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
