
package com.gyuyoung.eisrecorder

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {  // 하나의 화면

    override fun onCreate(savedInstanceState: Bundle?) {    // 화면이 만들어질때 자동으로 실행되는곳
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)  // 화면 모양 연결
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ---------------- 2단계: 카메라 스펙표 읽기 ----------------
        val report = readCameraSpecs()                           // 스펙표를 읽어서 글자로 만든다
        findViewById<TextView>(R.id.infoText).text = report      // XML에 만들어 둔 글자 칸(infoText)에 채운다
        report.lines().forEach { Log.d("EIS", it) }              // Logcat에도 한 줄씩 출력 (태그 "EIS"로 검색)
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
