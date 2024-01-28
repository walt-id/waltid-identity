package com.waltididentity.sampleapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.LocalKeyMetadata
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaunchInit()

        }
    }
}


@Composable
fun LaunchInit() {
    val scoper = rememberCoroutineScope()
    var textssad = ""

    LaunchedEffect(key1 = null){
        scoper.launch {
            val localKey = LocalKey.generate(KeyType.RSA, LocalKeyMetadata())
            println("Key ID: " + localKey.getKeyId())
            textssad = localKey.getKeyId()
        }
    }


    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold() {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it), contentAlignment = Alignment.Center
            ) {
                Column (
                    modifier = Modifier.fillMaxWidth()
                ){
                    Text(text = "WALT ID", fontWeight = FontWeight.Bold, fontSize = 36.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(text = textssad)
                }

            }
        }
    }


}
