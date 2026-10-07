
package com.gyuyoung.eisrecorder

import android.os.Bundle
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
    }
}