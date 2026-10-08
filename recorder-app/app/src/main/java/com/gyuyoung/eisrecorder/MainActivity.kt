
package com.gyuyoung.eisrecorder

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// 프로젝트 흐름

// 일단 manifest라는 안내판에서 시작 화면이 뭔지 받아옴 (mainactivity)

// 그 코틀린 파일인 여기를 실행하며 화면모양은 activity_main 으로 해달라고 선언

//코드는 화면을 켜는 역할, 모양은 XML이 담당

// 입구 화면이 만들어질 때 onCreate가 자동 호출됩니다.
// setContentView(R.layout.activity_main)이
// "화면 모양은 activity_main.xml로 해줘"라는 뜻입니다.

class MainActivity : AppCompatActivity(), SensorEventListener {  // 하나의 화면 (+ 센서 알림을 받겠다는 약속)

    // [3단계] 센서에 쓰는 변수들. 클래스 안에 두면 아래 여러 함수가 같이 쓸 수 있다
    private lateinit var sensorManager: SensorManager  // 센서 담당자 (lateinit = onCreate에서 나중에 채우겠다는 약속)
    private var gyro: Sensor? = null                   // 자이로 센서 (? = 폰에 없을 수도 있다는 표시)
    private lateinit var gyroText: TextView            // 실시간 값을 보여줄 글자 칸
    private var windowStartNs = 0L                     // 주기 계산용: 이번 구간이 시작된 센서 시각
    private var windowCount = 0                        // 주기 계산용: 이번 구간에서 받은 값의 개수

    // 센서 값을 받을 간격 (마이크로초, 1µs = 0.000001초). 이 값이 프로젝트의 "자이로 측정 주기" 변수다.
    // 8000µs = 0.008초 = 125Hz. 이 폰의 자이로가 허용하는 최소 간격(스펙표의 "최소 간격")과 같다.
    // - 0µs(가장 빠르게)로 요청하면: 권한(HIGH_SAMPLING_RATE_SENSORS)이 없다고 앱이 꺼졌다.
    // - 5000µs(200Hz)로 요청하면: 신청이 실패했다(센서가 허용하는 속도보다 빨라서로 추정).
    private val samplingPeriodUs = 8000

    override fun onCreate(savedInstanceState: Bundle?) {    // 화면이 만들어질때 자동으로 실행되는곳
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)  // 화면 모양 연결
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ---------------- 3단계: 자이로 센서 준비 ----------------
        gyroText = findViewById(R.id.gyroText)                                     // XML의 실시간 값 칸을 이름표로 찾는다
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager  // 센서 담당자 (어제 CameraManager와 같은 패턴)
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)               // 담당자에게 "자이로 센서 주세요"

        // ---------------- 2단계: 카메라 스펙표 읽기 ----------------
        val report = readGyroSpecs() + "\n" + readCameraSpecs()  // 자이로 스펙 + 카메라 스펙표를 합쳐서 글자로 만든다
        findViewById<TextView>(R.id.infoText).text = report      // XML에 만들어 둔 글자 칸(infoText)에 채운다
        report.lines().forEach { Log.d("EIS", it) }  // Logcat에도 한 줄씩 출력 (태그 "EIS"로 검색)
    }

    // ================= 3단계: 자이로 센서 값 받기 =================

    // 화면이 보이기 시작할 때 안드로이드가 자동으로 부른다 → 여기서 센서 알림을 신청한다
    override fun onResume() {
        super.onResume()
        windowStartNs = 0L  // 주기 계산을 처음부터 다시 시작
        // gyro가 있으면(?.let) 신청한다. samplingPeriodUs 간격으로 값을 알려달라는 뜻
        gyro?.let {
            // registerListener는 "신청이 받아들여졌는가"를 true/false로 돌려준다. 꼭 확인해야 한다!
            val ok = sensorManager.registerListener(this, it, samplingPeriodUs)
            Log.d("EIS", "자이로 신청 결과: $ok (간격 ${samplingPeriodUs}µs)")
            if (!ok) gyroText.text = "자이로 신청 실패! (간격 ${samplingPeriodUs}µs)"
        }
    }

    // 화면이 가려지면 자동으로 부른다 → 알림 신청을 취소한다. 안 하면 센서가 계속 켜져서 배터리를 쓴다
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // 자이로 값이 생길 때마다 안드로이드가 자동으로 불러주는 곳 (초당 수백 번!)
    // event.values = [x, y, z] 회전 속도(rad/s), event.timestamp = 값이 측정된 시각(나노초, 부팅 후 경과)
    override fun onSensorChanged(event: SensorEvent) {
        // 구간의 첫 값은 시작 시각만 기록하고 끝낸다
        if (windowStartNs == 0L) {
            windowStartNs = event.timestamp
            windowCount = 0
            return
        }
        windowCount++

        // 센서는 빠르게 받지만 화면은 0.25초에 한 번만 갱신한다 (화면을 너무 자주 바꾸면 앱이 느려진다)
        val elapsedNs = event.timestamp - windowStartNs
        if (elapsedNs < 250_000_000L) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())      // 세 축을 합친 전체 회전 속도
        val hz = windowCount * 1_000_000_000.0 / elapsedNs                   // 1초에 몇 번 도착했나
        val lagMs = (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000.0  // "지금 시각 - 센서 시각"(ms)

        gyroText.text = """
            자이로 (단위: rad/s)
            x: ${"%8.3f".format(x)}
            y: ${"%8.3f".format(y)}
            z: ${"%8.3f".format(z)}
            회전 속도: ${"%.3f".format(magnitude)} rad/s (약 ${"%.1f".format(Math.toDegrees(magnitude))} °/s)
            도착 주기: ${"%.0f".format(hz)} Hz (간격 ${"%.2f".format(1000.0 / hz)} ms)
            센서 시각: ${event.timestamp} ns
            지금 - 센서 시각: ${"%.2f".format(lagMs)} ms
        """.trimIndent()
        Log.d("EIS", "gyro x=${"%.3f".format(x)} y=${"%.3f".format(y)} z=${"%.3f".format(z)} | ${"%.0f".format(hz)} Hz | lag ${"%.2f".format(lagMs)} ms")

        windowStartNs = event.timestamp  // 다음 구간 시작
        windowCount = 0
    }

    // 센서 정확도가 바뀔 때 부르는 곳. 이번엔 안 쓰지만 SensorEventListener의 약속이라 꼭 적어야 한다
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // 자이로 센서의 "스펙표"를 글자로 만든다 (카메라 스펙표와 같은 역할)
    private fun readGyroSpecs(): String {
        val s = gyro ?: return "■ 자이로 센서: 이 폰에는 없음\n"
        val maxHz = if (s.minDelay > 0) 1_000_000 / s.minDelay else 0  // minDelay(마이크로초)로 최대 Hz를 계산
        return "■ 자이로 센서 스펙\n" +
            "  이름: ${s.name}\n" +
            "  제조사: ${s.vendor}\n" +
            "  최소 간격: ${s.minDelay} µs  (최대 약 $maxHz Hz)\n" +
            "  측정 범위: ±${s.maximumRange} rad/s\n" +
            "  해상도: ${s.resolution} rad/s\n"
    }

    // 폰의 모든 카메라의 "스펙표(CameraCharacteristics)"를 읽어서 하나의 글자로 만들어 돌려준다.
    // 스펙표는 카메라를 열지 않고도 읽을 수 있어서 카메라 권한이 필요 없다.
    private fun readCameraSpecs(): String {
        // CameraManager = 폰의 카메라 목록과 스펙표를 관리하는 담당자
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val sb = StringBuilder()  // 글자를 한 줄씩 이어 붙이는 도구
        sb.appendLine("폰: ${Build.MODEL}  (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        sb.appendLine()

        // cameraIdList = 카메라 번호표 목록. 번호표마다 반복해서 스펙표를 읽는다
        for (id in manager.cameraIdList) {
            val c = try {
                manager.getCameraCharacteristics(id)
            } catch (e: Exception) {
                sb.appendLine("■ 카메라 ID $id : 읽기 실패 (${e.message})")
                continue  // 이 카메라는 건너뛰고 다음 번호표로
            }

            // c.get(키) - 스펙표에서 항목 하나를 꺼냄
            sb.appendLine("■ 카메라 ID $id")
            sb.appendLine("  방향: ${facingName(c.get(CameraCharacteristics.LENS_FACING))}")
            sb.appendLine("  제어 수준: ${hardwareLevelName(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))}")

            // [게이트 1] 카메라 프레임 시각이 자이로와 같은 시계인가?
            sb.appendLine("  시간 기준: ${timestampSourceName(c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE))}")

            // [게이트 2] OIS(광학식 손떨림 보정)를 끌 수 있는가? OFF가 목록에 있으면 끌 수 있다
            sb.appendLine("  OIS 지원 모드: ${modeNames(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)) { onOffName(it) }}")

            // 갤럭시 기본 영상 안정화(비교 기준 B)를 켜고 끌 수 있는가?
            sb.appendLine("  영상 안정화 지원 모드: ${modeNames(c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)) { videoStabName(it) }}")

            // 아래 두 항목은 안드로이드 9(API 28) 이상에서만 있어서, 버전을 확인하고 읽는다
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // OIS 렌즈가 움직인 기록을 앱이 받아볼 수 있는가?
                sb.appendLine("  OIS 움직임 데이터 모드: ${modeNames(c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES)) { onOffName(it) }}")
                // 여러 카메라를 묶은 "가상 카메라"인지 (비어 있으면 단독 카메라)
                sb.appendLine("  구성 카메라: ${c.physicalCameraIds.ifEmpty { setOf("없음") }.joinToString(", ")}")
            }
            sb.appendLine()
        }

        sb.appendLine("※ 롤링 셔터 시간(SENSOR_ROLLING_SHUTTER_SKEW)은 스펙표가 아니라")
        sb.appendLine("   실제 촬영 결과에 들어 있어서, 카메라를 열어 찍는 단계에서 확인한다.")
        return sb.toString()
    }

    // 숫자 코드를 사람이 읽을 이름으로 바꿔주는 도우미 함수들 (그냥 번역표라서 이해 안 해도 됨)
    private fun facingName(v: Int?) = when (v) {
        CameraCharacteristics.LENS_FACING_BACK -> "후면"
        CameraCharacteristics.LENS_FACING_FRONT -> "전면"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "외부"
        else -> "알 수 없음($v)"
    }

    private fun hardwareLevelName(v: Int?) = when (v) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY (제한적)"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL (세밀한 제어 가능)"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3 (최상위)"
        else -> "알 수 없음($v)"
    }

    private fun timestampSourceName(v: Int?) = when (v) {
        CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME -> "REALTIME (자이로와 같은 시계)"
        CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> "UNKNOWN (자이로와 직접 비교 불가)"
        else -> "알 수 없음($v)"
    }

    private fun onOffName(m: Int) = when (m) {
        0 -> "OFF"
        1 -> "ON"
        else -> "기타($m)"
    }

    private fun videoStabName(m: Int) = when (m) {
        0 -> "OFF"
        1 -> "ON"
        2 -> "PREVIEW"
        else -> "기타($m)"
    }

    // 모드 번호 목록(예: [0, 1])을 "OFF, ON" 같은 글자로 바꾼다. 정보가 없으면(null) "정보 없음"
    private fun modeNames(arr: IntArray?, name: (Int) -> String): String =
        if (arr == null) "정보 없음" else arr.joinToString(", ") { name(it) }
}
