# 4~5주차 작업 전 준비 — 기록 앱 만들기 + 시간 정렬·OIS 확인

이번 주 목표: 영상과 자이로를 같은 시각 기준으로 동시에 기록하는 안드로이드 앱을 만들고,
카메라와 자이로의 시간이 맞는지 / OIS를 끌 수 있는지를 먼저 확인한다 (계획서의 1~2주차 게이트).

아직 코드는 작성하지 않는다. 이 문서는 시작 전에 알아야 할 것만 정리한다.

---

## 1. Android Studio · Kotlin 기초 (없으면 아무것도 못 만듦)

- **Android Studio 프로젝트 생성**: New Project → Empty Views Activity → 언어는 Kotlin 선택
- **최소한의 Kotlin 문법**: 변수(`val`/`var`), 함수(`fun`), 클래스, `override fun onCreate(...)` 같은 생명주기 함수 정도만 알면 시작 가능
- **폰에 앱 설치해서 실행하는 법**: 폰의 개발자 옵션 → USB 디버깅 켜기 → USB로 PC에 연결 → Android Studio 상단의 실행(▶) 버튼
- 참고: 구글 공식 "Android Basics with Compose" 또는 "Kotlin Bootcamp" 코드랩

**확인할 것**: 빈 프로젝트를 만들어서 폰 화면에 "Hello World"가 뜨는지부터 확인. 이게 안 되면 그 다음 단계로 못 간다.

---

## 2. 권한(Permission) 개념

카메라와 마이크처럼 민감한 기능은 앱이 실행 중에 사용자에게 직접 물어봐야 한다.

- `AndroidManifest.xml`에 `<uses-permission android:name="android.permission.CAMERA"/>` 선언
- 코드에서 `ActivityCompat.requestPermissions(...)`로 실행 중에 팝업을 띄워 허락받기
- 이번 프로젝트에 필요한 권한: 카메라, (저장할 파일 위치에 따라) 저장소 접근

---

## 3. SensorManager — 자이로 값 읽기

**핵심 개념**
- `SensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)`로 자이로 센서를 가져온다
- `SensorEventListener`를 등록하면 값이 들어올 때마다 `onSensorChanged(SensorEvent event)`가 호출된다
- `event.values`는 [x축, y축, z축] 각속도(rad/s)
- `event.timestamp`는 **부팅된 이후 지난 시간(나노초)** 이다. 사람이 읽는 시계(년월일시)가 아니라는 점이 중요 — 나중에 영상 시각과 맞출 때 기준이 된다.
- `SensorManager.registerListener(listener, sensor, samplingPeriodUs)`에서 세 번째 인자로 샘플링 주기를 정한다. `SensorManager.SENSOR_DELAY_FASTEST`가 가장 빠른 기본 옵션.
- Android 12(API 31)부터는 200Hz를 넘는 고속 샘플링에 `HIGH_SAMPLING_RATE_SENSORS` 권한이 추가로 필요할 수 있다 — 이번 주에 직접 확인해볼 부분.

**참고**: `TYPE_GYROSCOPE`는 자동으로 편향(bias)이 보정된 값이고, `TYPE_GYROSCOPE_UNCALIBRATED`는 보정 전 원본 값 + 추정 편향을 따로 준다. 이번 주는 `TYPE_GYROSCOPE`로 시작해도 충분하다.

---

## 4. Camera2 — 영상과 프레임 시각 얻기

Camera2는 안드로이드에서 카메라를 세밀하게 다루는 저수준 API다. (참고로 더 쉬운 CameraX도 있는데, 이번 프로젝트는 프레임 시각·OIS 같은 세부 값이 필요해서 Camera2를 쓴다.)

**핵심 개념**
- `CameraManager` → `CameraDevice` → `CaptureSession` → `CaptureRequest` 순서로 카메라를 연다
- 매 프레임마다 `CaptureResult`가 나오고, 여기서 `CaptureResult.SENSOR_TIMESTAMP`로 **그 프레임이 찍힌 시각(나노초)** 을 얻을 수 있다. 이것도 자이로처럼 부팅 이후 기준 시간이라, 조건이 맞으면 자이로 타임스탬프와 같은 시계를 쓰는 셈이 된다.
- `CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE` 값이 `REALTIME`이면 자이로와 같은 시간 기준을 쓰는 것이고, `UNKNOWN`이면 기준이 달라서 그대로 비교할 수 없다. **이번 주에 반드시 확인해야 할 값.**
- `CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW`: 프레임의 맨 윗줄과 맨 아랫줄이 찍히는 시각 차이(나노초). 이번 주엔 이 값이 얼마인지 읽어보기만 하면 된다(보정은 다음 단계). **정정**: 처음엔 이 값을 `CameraCharacteristics`(카메라 스펙표)에서 읽는다고 적었는데 틀렸다. 이 값은 **실제로 촬영할 때 나오는 결과(CaptureResult)** 에 들어 있어서, 카메라를 열어 찍기 시작한 뒤에야 읽을 수 있다(카메라 권한 필요).
- `CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE`: OIS를 켜고 끄는 설정. `OFF`로 지정 가능한지가 이번 주 확인 대상 (기기·카메라 앱 제조사에 따라 강제로 켜져 있을 수 있음).
- `CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE`: 갤럭시 기본 영상 안정화(EIS)를 켜고 끄는 설정. 7장 검증에서 B(갤럭시 기본) 촬영에 쓰인다.
- 영상 저장은 `MediaRecorder`나 `MediaCodec`으로 인코딩하면서 진행. 처음엔 `MediaRecorder`가 훨씬 간단하다.

---

## 5. 이번 주에 만들 것 (참고용 — 아직 만들지 말 것)

1. 권한 요청 화면
2. 미리보기 화면 + 녹화 시작/정지 버튼
3. 녹화 중 자이로 값을 타임스탬프와 함께 CSV 파일로 저장 (`시각, x, y, z` 한 줄씩)
4. 녹화된 영상 파일 (mp4)
5. 로그로 다음 값을 출력해서 확인: `SENSOR_INFO_TIMESTAMP_SOURCE`(스펙표에서 읽음), OIS 지원 모드(스펙표에서 읽음), `SENSOR_ROLLING_SHUTTER_SKEW`(촬영을 시작해야 읽을 수 있음), OIS를 OFF로 설정했을 때 실제로 적용되는지(촬영 결과로 확인)

---

## 6. 이번 주 게이트 체크리스트 (계획서 10장과 동일)

- [ ] 카메라 프레임 타임스탬프와 자이로 타임스탬프가 같은 시간 기준(둘 다 부팅 이후 나노초)을 쓰는지 확인
- [ ] `SENSOR_INFO_TIMESTAMP_SOURCE`가 `REALTIME`인지 확인
- [ ] `LENS_OPTICAL_STABILIZATION_MODE`를 `OFF`로 설정했을 때 반영되는지 확인 (안 되면 한계로 기록)
- [ ] 자이로를 고속으로 읽을 수 있는지(권한 필요 여부) 확인

세 개 중 하나라도 안 되면 계획서에 적어둔 대로 원인을 기록하고, 정말 막히면 대안 주제(가변 주사율)로 전환을 검토한다.

---

## 7. 막혔을 때 검색할 키워드

- "Camera2 SENSOR_TIMESTAMP gyroscope synchronization"
- "Android CaptureResult rolling shutter skew"
- "Camera2 LENS_OPTICAL_STABILIZATION_MODE"
- "SensorManager TYPE_GYROSCOPE sampling rate HIGH_SAMPLING_RATE_SENSORS"
- "Android MediaRecorder Camera2 record video"

공식 문서(developer.android.com)가 1순위, 안 되면 Stack Overflow에서 기종별 사례를 찾아본다.
