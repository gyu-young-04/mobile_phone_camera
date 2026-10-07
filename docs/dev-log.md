# 개발 일지

배운 것과 막혔던 것을 기록한다. 나중에 기억이 안 날 때, 최종 보고서의 "개발 과정"을 쓸 때 다시 본다.

---

## 2026-10-07 — 1단계: 프로젝트 생성과 Hello World

### 한 일
- Git, Python 3.12(+ opencv-python, numpy, scikit-learn), Android Studio 설치
- 폰(Galaxy S25 FE, SM-S731N, Android 16, One UI 8.5)을 USB 디버깅으로 PC에 연결
- 안드로이드 프로젝트 `recorder-app` 생성 → 폰에서 ▶ 실행 → "Hello World!" 확인
- 첫 커밋 `feat: 안드로이드 프로젝트 초기 생성 (Hello World 실행 확인)` 후 GitHub에 push

### 용어 정리
| 용어 | 뜻 |
|---|---|
| Android Studio | 앱을 만드는 공작소 |
| 프로젝트 | 앱의 설계도. 실행하면 APK로 조립해서 폰에 설치한다 |
| SDK | 앱을 만드는 데 필요한 도구 상자(안드로이드 부품 + 조립 도구 + adb + 에뮬레이터) |
| APK | 폰에 설치되는 앱 파일 |
| package name | 앱의 고유 ID (`com.gyuyoung.eisrecorder`). 폰이 이 이름으로 앱을 구분한다 |
| USB 디버깅 | 폰이 PC에게 "개발자 권한으로 폰을 다뤄도 좋다"고 허락해주는 스위치. 앱 설치, 로그 보기, 파일 가져오기가 가능해진다 |
| ADB | Android Debug Bridge. PC와 폰을 잇는 통로 역할을 하는 프로그램 |
| Activity | 앱의 화면 하나. MainActivity가 첫 화면이다 |
| Commit / Push | Commit은 내 컴퓨터에 저장, Push는 GitHub에 올리기. 둘은 별개 |

폴더 이름을 `recorder-app`으로 한 건 필수가 아니라 편의(문서 `docs`, 앱, 나중에 분석 스크립트를 구분)를 위해서다.

### Hello World가 뜨기까지의 흐름
```
앱 아이콘 터치
   ↓
① AndroidManifest.xml   폰(안드로이드)이 읽는 안내판. "MainActivity가 시작 화면(LAUNCHER)이야"
   ↓
② MainActivity.kt        시작 화면의 동작 코드. onCreate()가 실행된다
   ↓ setContentView(R.layout.activity_main)   ← 화면 모양을 XML과 짝지어주는 줄
③ activity_main.xml      TextView(text="Hello World!")를 화면 가운데 배치
   ↓
폰 화면에 Hello World
```
- 폰이 Manifest를 읽고 시작 화면(MainActivity)을 켠다.
- MainActivity는 **동작**(계산, 버튼, 센서 처리)을 담당하고, 화면 **모양**은 짝지은 XML이 담당한다. 코드는 화면을 켜고, 모양은 XML이 맡는다.

### 앞으로 고칠 파일
| 파일 | 역할 | 이 프로젝트에서 |
|---|---|---|
| `MainActivity.kt` | 화면 동작 코드 | 센서·카메라 코드를 추가 |
| `activity_main.xml` | 화면 모양 | 녹화 버튼, 미리보기 화면 배치 |
| `AndroidManifest.xml` | 앱 신분증, 권한 선언 | 카메라 권한(`CAMERA`)을 추가 |

### 막혔던 것
- **폰이 ADB에 안 잡힘**: Windows는 폰을 파일 전송 모드로 인식하는데 `adb devices`는 비어 있었다.
- **원인**: 개발자 모드가 아직 켜지지 않았다. 설정 검색에서 `개발자`를 쳐도 "개발자 옵션"이 안 나오고 "개발자 설정"만 나온 게 신호였다.
- **해결**: 설정 → 휴대전화 정보 → 소프트웨어 정보 → **빌드번호 7번 탭** → 개발자 옵션 생성 → USB 디버깅 켜기 → 폰의 "USB 디버깅 허용" 팝업에서 허용.
- **참고**: 삼성 한국어 메뉴에서 Auto Blocker 이름은 "자동 차단기"가 아니라 **"보안 위험 자동 차단"** 이다. 켜져 있으면 USB 디버깅이 막힐 수 있다(이번엔 꺼져 있어서 원인이 아니었다).

### 코드 읽는 최소 기초
| 코틀린 | 뜻 |
|---|---|
| `class MainActivity : AppCompatActivity()` | "MainActivity라는 화면 틀을 만든다" |
| `fun 이름() { }` | 이름 붙은 동작 묶음(함수) |
| `override fun onCreate(...)` | 안드로이드가 정해둔 동작(화면이 만들어질 때)을 우리 방식으로 채움 |
| `val` / `var` | `val`은 못 바꾸는 값, `var`는 바꿀 수 있는 값 |
| `// 설명` | 한 줄 주석 (XML은 `<!-- 설명 -->`) |

Kotlin은 안드로이드 앱을 만드는 프로그래밍 언어이고, Java는 원조 언어다. 두 언어는 호환되며 지금 프로젝트에는 Java 코드가 없다. 인터넷 예제 중에는 Java가 많아서 읽을 수 있으면 도움이 된다.

---

## 2026-10-07 — 2단계: 카메라 스펙표 읽기 (첫 번째 게이트 확인)

### 한 일
- `MainActivity.kt`에서 폰의 모든 카메라 **스펙표(CameraCharacteristics)** 를 읽어 화면과 Logcat(태그 `EIS`)에 출력
- `activity_main.xml`의 Hello World를 `ScrollView` + `TextView(id=infoText)`로 교체
- 카메라를 열지 않고 스펙표만 읽기 때문에 **카메라 권한이 필요 없다**

### 개념
- **스펙표(CameraCharacteristics)**: 카메라가 원래 가진 고정 성능. 카메라를 열지 않고 읽을 수 있다.
- **촬영 결과(CaptureResult)**: 찍는 순간에 나오는 정보. 롤링 셔터 시간(`SENSOR_ROLLING_SHUTTER_SKEW`)이 여기에 들어 있어서 카메라를 열어 찍어야 읽을 수 있다. (이전 문서에 스펙표에서 읽는다고 잘못 적었던 것을 정정함)
- **CameraManager**: 카메라 목록과 스펙표를 관리하는 담당자. `cameraIdList`는 카메라 번호표 목록.
- **스펙표에 "지원한다"고 적혀 있는 것과 실제로 동작하는 것은 다르다.** 실제 동작은 촬영 단계에서 다시 확인해야 한다.

### 결과 (SM-S731N, Android 16)
| 카메라 ID | 방향 | 제어 수준 | 시간 기준 | OIS 모드 | 영상 안정화 모드 | OIS 데이터 | 구성 카메라 |
|---|---|---|---|---|---|---|---|
| 0 | 후면 | FULL | REALTIME | OFF, ON | OFF, ON, PREVIEW | 정보 없음 | 2, 5, 6 |
| 1 | 전면 | LIMITED | REALTIME | OFF | OFF | 정보 없음 | 없음 |
| 2 | 후면 | LIMITED | REALTIME | OFF | OFF | 정보 없음 | 없음 |
| 3 | 전면 | LIMITED | REALTIME | OFF | OFF | 정보 없음 | 없음 |

### 해석 (게이트 판정)
| 확인 항목 | 결과 | 판정 |
|---|---|---|
| 카메라 시간 기준이 자이로와 같은 시계인가 | 전 카메라 `REALTIME` | 스펙표 기준 통과 (실제 촬영에서 재확인 필요) |
| OIS를 끌 수 있는가 | ID 0의 지원 모드에 `OFF` 포함 | 스펙표 기준 통과 (실제로 꺼지는지 촬영 결과로 확인 필요) |
| 갤럭시 기본 영상 안정화(비교 기준 B)를 앱에서 켜고 끌 수 있는가 | ID 0에 `OFF, ON, PREVIEW` | 가능 |
| OIS 렌즈 움직임 데이터를 받을 수 있는가 | 모든 카메라 "정보 없음" | 불가. 선택 사항이었으므로 영향 없음 |

### 새로 알게 된 것
- **ID 0은 여러 카메라를 묶은 "논리 카메라"** (구성: 물리 카메라 2, 5, 6). 일반 목록에는 0~3번만 보이고, 5·6번은 ID 0을 통해서만 접근되는 것으로 보인다.
- **제어 수준이 FULL인 건 ID 0뿐**이고 OIS/영상 안정화 제어도 ID 0에서만 노출된다. 그래서 **기록 앱은 ID 0으로 촬영**하는 것이 합리적이다.
- 갤럭시 기본 안정화는 Camera2의 `영상 안정화 모드 ON`이고, 삼성 카메라 앱의 "슈퍼 스테디"와 같은 것인지는 아직 확인 못 했다. 비교 기준 B를 둘 중 무엇으로 할지는 이후에 결정한다.

### 아직 모르는 것 (다음 단계에서 확인)
- 자이로 최대 샘플링 주기와 `HIGH_SAMPLING_RATE_SENSORS` 권한 필요 여부 (3단계)
- 롤링 셔터 시간 값 (카메라를 열어 찍는 단계)
- OIS를 `OFF`로 요청했을 때 실제로 꺼지는지 (촬영 결과의 `LENS_OPTICAL_STABILIZATION_MODE`로 확인)
- 프레임 시각과 자이로 시각 사이에 남는 미세한 어긋남 (교차상관으로 추정 예정)
