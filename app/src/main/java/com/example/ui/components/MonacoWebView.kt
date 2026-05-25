package com.example.ui.components

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonacoWebView(
    code: String,
    language: String,
    onCodeChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lastContent = remember { object { var value: String = code } }

    val monacoHtml = remember(language) {
        val extensionMap = mapOf(
            "html" to "html", "htm" to "html",
            "css" to "css",
            "js" to "javascript", "jsx" to "javascript",
            "ts" to "typescript", "tsx" to "typescript",
            "py" to "python",
            "json" to "json",
            "md" to "markdown",
            "sh" to "shell"
        )
        val mappedLanguage = extensionMap[language.lowercase()] ?: "javascript"

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                html, body, #editor {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    background-color: #000000;
                }
                /* Hide monaco-editor overflows and style scrollbars */
                .monaco-scrollable-element .scrollbar {
                    background: #10131a;
                }
            </style>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/require.js/2.3.6/require.min.js"></script>
        </head>
        <body>
            <div id="editor"></div>
            <script>
                function b64Decode(str) {
                    try {
                        return decodeURIComponent(atob(str).split('').map(function(c) {
                            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
                        }).join(''));
                    } catch(e) {
                        return atob(str);
                    }
                }

                var editor;
                var currentCode = b64Decode("${encodeBase64(code)}");
                var isSettingValue = false;
                
                require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.41.0/min/vs' }});
                require(['vs/editor/editor.main'], function() {
                    // Create beautiful dark theme with black background and matching gutter
                    monaco.editor.defineTheme('droidide-black', {
                        base: 'vs-dark',
                        inherit: true,
                        rules: [
                            { token: 'comment', foreground: '6a9955', fontStyle: 'italic' },
                            { token: 'keyword', foreground: '569cd6' },
                            { token: 'string', foreground: 'ce9178' },
                            { token: 'number', foreground: 'b5cea8' },
                            { token: 'regexp', foreground: 'd16969' },
                            { token: 'type', foreground: '4ec9b0' },
                            { token: 'class', foreground: '4ec9b0' },
                            { token: 'function', foreground: 'dcdcaa' },
                            { token: 'variable', foreground: '9cdcfe' }
                        ],
                        colors: {
                            'editor.background': '#000000',
                            'editorGutter.background': '#0b0e14',
                            'editorLineNumber.foreground': '#8a919d',
                            'editorLineNumber.activeForeground': '#e1e2eb',
                            'editor.lineHighlightBackground': '#10131a',
                            'editor.selectionBackground': '#264f78'
                        }
                    });

                    editor = monaco.editor.create(document.getElementById('editor'), {
                        value: currentCode,
                        language: '$mappedLanguage',
                        theme: 'droidide-black',
                        automaticLayout: true,
                        fontSize: 14,
                        minimap: { enabled: false },
                        scrollbar: {
                            vertical: 'visible',
                            horizontal: 'visible',
                            useShadows: false,
                            verticalScrollbarSize: 10,
                            horizontalScrollbarSize: 10
                        }
                    });

                    editor.getModel().onDidChangeContent(function() {
                        if (isSettingValue) return;
                        var updatedCode = editor.getValue();
                        AndroidBridge.onContentChanged(updatedCode);
                    });
                });

                function updateCode(b64NewCode) {
                    var newCode = b64Decode(b64NewCode);
                    if (editor) {
                        if (editor.getValue() !== newCode) {
                            isSettingValue = true;
                            editor.setValue(newCode);
                            isSettingValue = false;
                        }
                    } else {
                        currentCode = newCode;
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    val bridge = remember {
        object {
            @JavascriptInterface
            fun onContentChanged(newContent: String) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    lastContent.value = newContent
                    onCodeChanged(newContent)
                }
            }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                
                addJavascriptInterface(bridge, "AndroidBridge")
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                    }
                }
                
                loadDataWithBaseURL("https://localhost", monacoHtml, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            if (lastContent.value != code) {
                lastContent.value = code
                webView.evaluateJavascript("updateCode('${encodeBase64(code)}');", null)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

private fun encodeBase64(str: String): String {
    return Base64.encodeToString(str.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}
