package com.ab25cq.memo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ab25cq.memo.databinding.ActivityMemoViewBinding
import com.ab25cq.memo.viewmodel.MemoViewModel
import kotlinx.coroutines.launch

class MemoViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMemoViewBinding
    private val viewModel: MemoViewModel by viewModels()
    private var memoId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        memoId = intent.getLongExtra("memo_id", -1)
        val previewTitle = intent.getStringExtra("preview_title")
        val previewContent = intent.getStringExtra("preview_content")

        setupWebView()

        if (previewContent != null) {
            supportActionBar?.title = previewTitle?.ifEmpty { "(無題)" } ?: "(プレビュー)"
            loadContent(previewTitle ?: "", previewContent)
        } else if (memoId != -1L) {
            lifecycleScope.launch {
                viewModel.getAllMemosSync().find { it.id == memoId }?.let { memo ->
                    supportActionBar?.title = memo.title.ifEmpty { "(無題)" }
                    loadContent(memo.title, memo.content)
                }
            }
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }
    }

    private fun loadContent(title: String, content: String) {
        val html = buildViewerHtml(title, content)
        binding.webView.loadDataWithBaseURL(
            "https://cdn.jsdelivr.net/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun buildViewerHtml(title: String, content: String): String = """
        <!DOCTYPE html>
        <html lang="ja">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
          <style>
            body { font-family: 'Noto Sans JP', sans-serif; padding: 16px; line-height: 1.8; color: #212121; background: #fafafa; }
            h1 { font-size: 1.5em; border-bottom: 2px solid #1976D2; padding-bottom: 8px; color: #1976D2; }
            h2 { font-size: 1.3em; color: #1565C0; }
            h3 { font-size: 1.1em; }
            img { max-width: 100%; height: auto; border-radius: 4px; margin: 8px 0; }
            pre { background: #f5f5f5; padding: 12px; border-radius: 4px; overflow-x: auto; font-size: 0.9em; }
            code { background: #f0f0f0; padding: 2px 6px; border-radius: 3px; font-family: monospace; }
            .katex-display { overflow-x: auto; padding: 8px 0; }
            .katex { font-size: 1.1em; }
            blockquote { border-left: 4px solid #1976D2; margin: 0; padding-left: 16px; color: #555; }
            table { border-collapse: collapse; width: 100%; margin: 8px 0; }
            th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
            th { background: #e3f2fd; }
          </style>
        </head>
        <body>
          <div id="content">$content</div>
          <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
          <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>
          <script>
            // ── Keep等でmath-delimiterが消えた \lambda 等を自動補完 ──
            var LATEX_CMD_NAMES = [
              'alpha','beta','gamma','delta','epsilon','varepsilon','zeta','eta',
              'theta','vartheta','iota','kappa','lambda','mu','nu','xi','pi','varpi',
              'rho','varrho','sigma','varsigma','tau','upsilon','phi','varphi','chi',
              'psi','omega','Alpha','Beta','Gamma','Delta','Epsilon','Zeta','Eta',
              'Theta','Iota','Kappa','Lambda','Mu','Nu','Xi','Pi','Rho','Sigma',
              'Tau','Upsilon','Phi','Chi','Psi','Omega',
              'sum','prod','int','oint','iint','iiint','lim','limsup','liminf',
              'sup','inf','max','min','gcd',
              'infty','partial','nabla','hbar','ell','wp','aleph',
              'sqrt','frac','dfrac','tfrac','binom','over','choose',
              'cdot','times','div','pm','mp','oplus','otimes','circ','bullet',
              'leq','geq','neq','approx','equiv','sim','simeq','cong','propto','ll','gg',
              'rightarrow','leftarrow','Rightarrow','Leftarrow','leftrightarrow',
              'Leftrightarrow','to','gets','mapsto','longrightarrow',
              'in','notin','ni','subset','supset','subseteq','supseteq',
              'cup','cap','emptyset','varnothing','setminus',
              'forall','exists','nexists','neg','lnot','wedge','vee','land','lor',
              'vec','hat','bar','tilde','dot','ddot','breve','acute','grave',
              'overline','underline','widehat','widetilde','overbrace','underbrace',
              'sin','cos','tan','sec','csc','cot','arcsin','arccos','arctan',
              'sinh','cosh','tanh','coth',
              'log','ln','exp','det','dim','ker','hom','arg','deg','Re','Im',
              'ldots','cdots','vdots','ddots',
              'left','right','big','Big','bigg','Bigg','middle',
              'text','operatorname','mbox',
              'mathbb','mathbf','mathit','mathrm','mathcal','mathfrak','boldsymbol',
              'not','quad','qquad','begin','end',
            ];
            var _CMDS = LATEX_CMD_NAMES.join('|');
            var _hasLatexRe = new RegExp('\\\\(?:' + _CMDS + ')\\b');

            function autoAddMathDelimiters(text) {
              if (!_hasLatexRe.test(text)) return text;
              var saved = [];
              var s = text.replace(/\$\$[\s\S]*?\$\$|\$[^$\n]*?\$/g, function(m) {
                saved.push(m);
                return '\x00' + (saved.length - 1) + '\x00';
              });
              var braceArg  = '(?:\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\})';
              var scriptArg = '(?:[_^](?:' + braceArg + '|[^\\s{}]))';
              var tokenRe   = new RegExp(
                '\\\\(?:' + _CMDS + ')\\b' +
                '(?:' + braceArg  + ')*' +
                '(?:' + scriptArg + ')*', 'g'
              );
              s = s.replace(tokenRe, function(m) { return '$' + m + '$'; });
              // 隣接する [math] op [math] をひとつの $...$ にマージ
              var mergeRe = /\$([^$\n]+)\$(\s*(?:[=+\-*\/×÷·<>≤≥≠≈^,]|\\(?:cdot|times|div|pm|leq|geq|neq|approx|to|rightarrow|in))\s*)\$([^$\n]+)\$/g;
              for (var i = 0; i < 5; i++) {
                s = s.replace(mergeRe, function(_, a, op, b) { return '$' + a + op + b + '$'; });
              }
              s = s.replace(/\x00(\d+)\x00/g, function(_, i) { return saved[+i]; });
              return s;
            }

            function preprocessLatexInElement(el) {
              var SKIP = {'SCRIPT':1,'STYLE':1,'CODE':1,'PRE':1,'TEXTAREA':1};
              var walker = document.createTreeWalker(
                el, NodeFilter.SHOW_TEXT,
                { acceptNode: function(n) {
                    var p = n.parentElement;
                    while (p && p !== el) {
                      if (SKIP[p.tagName] || p.classList.contains('katex') ||
                          p.classList.contains('math-inline') ||
                          p.classList.contains('math-block'))
                        return NodeFilter.FILTER_REJECT;
                      p = p.parentElement;
                    }
                    return NodeFilter.FILTER_ACCEPT;
                  }
                }
              );
              var nodes = [];
              var n;
              while ((n = walker.nextNode())) nodes.push(n);
              nodes.forEach(function(node) {
                var processed = autoAddMathDelimiters(node.textContent);
                if (processed !== node.textContent) node.textContent = processed;
              });
            }

            var contentEl = document.getElementById('content');
            preprocessLatexInElement(contentEl);

            // 旧バージョン保存バグ後方互換: \\cmd → \cmd (バックスラッシュ二重エスケープ修正)
            (function(el) {
              var w = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
              var nodes = [], n;
              while ((n = w.nextNode())) nodes.push(n);
              nodes.forEach(function(node) {
                var t = node.textContent;
                if (t.indexOf('\\\\') < 0) return;
                node.textContent = t.replace(/\\\\([a-zA-Z\[{\]()\\ ])/g, '\\$1');
              });
            })(contentEl);

            renderMathInElement(contentEl, {
              delimiters: [
                {left: "${'$'}${'$'}", right: "${'$'}${'$'}", display: true},
                {left: "${'$'}", right: "${'$'}", display: false},
                {left: "\\(", right: "\\)", display: false},
                {left: "\\[", right: "\\]", display: true}
              ],
              throwOnError: false,
              errorColor: '#e53935'
            });
            window.scrollTo(0, document.body.scrollHeight);
          </script>
        </body>
        </html>
    """.trimIndent()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (memoId != -1L) menuInflater.inflate(R.menu.menu_view, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_edit -> {
                startActivity(Intent(this, MemoEditActivity::class.java).putExtra("memo_id", memoId))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
