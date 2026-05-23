package com.ab25cq.memo

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*

object MathWizard {

    data class Param(val labelResId: Int, val default: String = "")

    data class Template(
        val categoryResId: Int,
        val nameResId: Int,
        val preview: String,
        val params: List<Param>,
        val display: Boolean = false,
        val build: (List<String>) -> String
    )

    private val TEMPLATES = listOf(
        // ── Basic ────────────────────────────────────────────
        Template(R.string.cat_basic, R.string.tmpl_fraction, "\\frac{a}{b}",
            listOf(Param(R.string.param_numerator, "a"), Param(R.string.param_denominator, "b"))) { p ->
            "\\frac{${p[0]}}{${p[1]}}" },

        Template(R.string.cat_basic, R.string.tmpl_power, "a^{n}",
            listOf(Param(R.string.param_base, "a"), Param(R.string.param_exponent, "n"))) { p ->
            "${p[0]}^{${p[1]}}" },

        Template(R.string.cat_basic, R.string.tmpl_subscript, "a_{i}",
            listOf(Param(R.string.param_char, "a"), Param(R.string.param_subscript_lbl, "i"))) { p ->
            "${p[0]}_{${p[1]}}" },

        Template(R.string.cat_basic, R.string.tmpl_power_sub, "a_{i}^{n}",
            listOf(Param(R.string.param_char, "a"), Param(R.string.param_subscript_lbl, "i"), Param(R.string.param_exponent, "n"))) { p ->
            "${p[0]}_{${p[1]}}^{${p[2]}}" },

        Template(R.string.cat_basic, R.string.tmpl_sqrt, "\\sqrt{x}",
            listOf(Param(R.string.param_content, "x"))) { p ->
            "\\sqrt{${p[0]}}" },

        Template(R.string.cat_basic, R.string.tmpl_nth_root, "\\sqrt[n]{x}",
            listOf(Param(R.string.param_degree_n, "3"), Param(R.string.param_content, "x"))) { p ->
            "\\sqrt[${p[0]}]{${p[1]}}" },

        Template(R.string.cat_basic, R.string.tmpl_abs, "\\left|x\\right|",
            listOf(Param(R.string.param_content, "x"))) { p ->
            "\\left|${p[0]}\\right|" },

        Template(R.string.cat_basic, R.string.tmpl_binom, "\\binom{n}{k}",
            listOf(Param(R.string.param_base, "n"), Param(R.string.param_exponent, "k"))) { p ->
            "\\binom{${p[0]}}{${p[1]}}" },

        Template(R.string.cat_basic, R.string.tmpl_pm, "a \\pm b",
            listOf(Param(R.string.param_left, "a"), Param(R.string.param_right, "b"))) { p ->
            "${p[0]} \\pm ${p[1]}" },

        // ── Calculus ─────────────────────────────────────────
        Template(R.string.cat_calculus, R.string.tmpl_sum, "\\sum_{i=1}^{n}",
            listOf(Param(R.string.param_variable, "i"), Param(R.string.param_lower_bound, "1"), Param(R.string.param_upper_bound, "n")), display = true) { p ->
            "\\sum_{${p[0]}=${p[1]}}^{${p[2]}}" },

        Template(R.string.cat_calculus, R.string.tmpl_prod, "\\prod_{i=1}^{n}",
            listOf(Param(R.string.param_variable, "i"), Param(R.string.param_lower_bound, "1"), Param(R.string.param_upper_bound, "n")), display = true) { p ->
            "\\prod_{${p[0]}=${p[1]}}^{${p[2]}}" },

        Template(R.string.cat_calculus, R.string.tmpl_integral_def, "\\int_{a}^{b} f\\,dx",
            listOf(Param(R.string.param_lower_bound, "a"), Param(R.string.param_upper_bound, "b"), Param(R.string.param_integrand, "f(x)"), Param(R.string.param_variable, "x")), display = true) { p ->
            "\\int_{${p[0]}}^{${p[1]}} ${p[2]}\\,d${p[3]}" },

        Template(R.string.cat_calculus, R.string.tmpl_integral_indef, "\\int f\\,dx",
            listOf(Param(R.string.param_integrand, "f(x)"), Param(R.string.param_variable, "x")), display = true) { p ->
            "\\int ${p[0]}\\,d${p[1]}" },

        Template(R.string.cat_calculus, R.string.tmpl_limit, "\\lim_{x \\to a}",
            listOf(Param(R.string.param_variable, "x"), Param(R.string.param_limit_to, "a"))) { p ->
            "\\lim_{${p[0]} \\to ${p[1]}}" },

        Template(R.string.cat_calculus, R.string.tmpl_deriv, "\\frac{dy}{dx}",
            listOf(Param(R.string.param_dy, "y"), Param(R.string.param_dx, "x"))) { p ->
            "\\frac{d${p[0]}}{d${p[1]}}" },

        Template(R.string.cat_calculus, R.string.tmpl_partial, "\\frac{\\partial f}{\\partial x}",
            listOf(Param(R.string.param_function, "f"), Param(R.string.param_variable, "x"))) { p ->
            "\\frac{\\partial ${p[0]}}{\\partial ${p[1]}}" },

        Template(R.string.cat_calculus, R.string.tmpl_partial2, "\\frac{\\partial^2 f}{\\partial x^2}",
            listOf(Param(R.string.param_function, "f"), Param(R.string.param_variable, "x"))) { p ->
            "\\frac{\\partial^2 ${p[0]}}{\\partial ${p[1]}^2}" },

        // ── Functions ────────────────────────────────────────
        Template(R.string.cat_functions, R.string.tmpl_trig, "\\sin(x)",
            listOf(Param(R.string.param_fn_name, "sin"), Param(R.string.param_argument, "x"))) { p ->
            "\\${p[0]}\\left(${p[1]}\\right)" },

        Template(R.string.cat_functions, R.string.tmpl_log, "\\log_{a} x",
            listOf(Param(R.string.param_base_a, "a"), Param(R.string.param_mantissa, "x"))) { p ->
            "\\log_{${p[0]}} ${p[1]}" },

        Template(R.string.cat_functions, R.string.tmpl_ln, "\\ln x",
            listOf(Param(R.string.param_mantissa, "x"))) { p ->
            "\\ln ${p[0]}" },

        Template(R.string.cat_functions, R.string.tmpl_exp, "e^{x}",
            listOf(Param(R.string.param_exponent, "x"))) { p ->
            "e^{${p[0]}}" },

        Template(R.string.cat_functions, R.string.tmpl_floor, "\\lfloor x \\rfloor",
            listOf(Param(R.string.param_content, "x"))) { p ->
            "\\lfloor ${p[0]} \\rfloor" },

        Template(R.string.cat_functions, R.string.tmpl_ceil, "\\lceil x \\rceil",
            listOf(Param(R.string.param_content, "x"))) { p ->
            "\\lceil ${p[0]} \\rceil" },

        // ── Linear Algebra ───────────────────────────────────
        Template(R.string.cat_linear_algebra, R.string.tmpl_vec_arrow, "\\vec{v}",
            listOf(Param(R.string.param_char, "v"))) { p ->
            "\\vec{${p[0]}}" },

        Template(R.string.cat_linear_algebra, R.string.tmpl_vec_bold, "\\mathbf{v}",
            listOf(Param(R.string.param_char, "v"))) { p ->
            "\\mathbf{${p[0]}}" },

        Template(R.string.cat_linear_algebra, R.string.tmpl_norm, "\\|v\\|",
            listOf(Param(R.string.param_vector, "v"))) { p ->
            "\\left\\|${p[0]}\\right\\|" },

        Template(R.string.cat_linear_algebra, R.string.tmpl_inner_prod, "\\langle u, v \\rangle",
            listOf(Param(R.string.param_vec1, "u"), Param(R.string.param_vec2, "v"))) { p ->
            "\\langle ${p[0]},\\, ${p[1]} \\rangle" },

        Template(R.string.cat_linear_algebra, R.string.tmpl_transpose, "A^{T}",
            listOf(Param(R.string.param_matrix, "A"))) { p ->
            "${p[0]}^{T}" },

        Template(R.string.cat_linear_algebra, R.string.tmpl_inv, "A^{-1}",
            listOf(Param(R.string.param_matrix, "A"))) { p ->
            "${p[0]}^{-1}" },

        Template(R.string.cat_linear_algebra, R.string.tmpl_det, "\\det(A)",
            listOf(Param(R.string.param_matrix, "A"))) { p ->
            "\\det(${p[0]})" },

        Template(R.string.cat_linear_algebra, R.string.tmpl_mat2, "\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}",
            listOf(Param(R.string.param_pos_11, "a"), Param(R.string.param_pos_12, "b"),
                   Param(R.string.param_pos_21, "c"), Param(R.string.param_pos_22, "d")), display = true) { p ->
            "\\begin{pmatrix} ${p[0]} & ${p[1]} \\\\ ${p[2]} & ${p[3]} \\end{pmatrix}" },

        Template(R.string.cat_linear_algebra, R.string.tmpl_mat3, "\\begin{pmatrix}a&b&c\\\\d&e&f\\\\g&h&i\\end{pmatrix}",
            listOf(Param(R.string.param_pos_11, "a"), Param(R.string.param_pos_12, "b"), Param(R.string.param_pos_13, "c"),
                   Param(R.string.param_pos_21, "d"), Param(R.string.param_pos_22, "e"), Param(R.string.param_pos_23, "f"),
                   Param(R.string.param_pos_31, "g"), Param(R.string.param_pos_32, "h"), Param(R.string.param_pos_33, "i")), display = true) { p ->
            "\\begin{pmatrix} ${p[0]} & ${p[1]} & ${p[2]} \\\\ ${p[3]} & ${p[4]} & ${p[5]} \\\\ ${p[6]} & ${p[7]} & ${p[8]} \\end{pmatrix}" },

        // ── Decorators ───────────────────────────────────────
        Template(R.string.cat_decoration, R.string.tmpl_overline, "\\overline{x}",
            listOf(Param(R.string.param_content, "x"))) { p ->
            "\\overline{${p[0]}}" },

        Template(R.string.cat_decoration, R.string.tmpl_hat, "\\hat{x}",
            listOf(Param(R.string.param_char, "x"))) { p ->
            "\\hat{${p[0]}}" },

        Template(R.string.cat_decoration, R.string.tmpl_tilde_dec, "\\tilde{x}",
            listOf(Param(R.string.param_char, "x"))) { p ->
            "\\tilde{${p[0]}}" },

        Template(R.string.cat_decoration, R.string.tmpl_dot, "\\dot{x}",
            listOf(Param(R.string.param_char, "x"))) { p ->
            "\\dot{${p[0]}}" },

        Template(R.string.cat_decoration, R.string.tmpl_ddot, "\\ddot{x}",
            listOf(Param(R.string.param_char, "x"))) { p ->
            "\\ddot{${p[0]}}" },

        Template(R.string.cat_decoration, R.string.tmpl_overbrace, "\\overbrace{a+b}^{n}",
            listOf(Param(R.string.param_content, "a+b"), Param(R.string.param_upper_label, "n")), display = true) { p ->
            "\\overbrace{${p[0]}}^{\\text{${p[1]}}}" },

        Template(R.string.cat_decoration, R.string.tmpl_underbrace, "\\underbrace{a+b}_{n}",
            listOf(Param(R.string.param_content, "a+b"), Param(R.string.param_lower_label, "n")), display = true) { p ->
            "\\underbrace{${p[0]}}_{\\text{${p[1]}}}" },

        // ── Sets & Logic ─────────────────────────────────────
        Template(R.string.cat_set_logic, R.string.tmpl_elem_in, "x \\in A",
            listOf(Param(R.string.param_element, "x"), Param(R.string.param_set, "A"))) { p ->
            "${p[0]} \\in ${p[1]}" },

        Template(R.string.cat_set_logic, R.string.tmpl_elem_notin, "x \\notin A",
            listOf(Param(R.string.param_element, "x"), Param(R.string.param_set, "A"))) { p ->
            "${p[0]} \\notin ${p[1]}" },

        Template(R.string.cat_set_logic, R.string.tmpl_subset, "A \\subseteq B",
            listOf(Param(R.string.param_set1, "A"), Param(R.string.param_set2, "B"))) { p ->
            "${p[0]} \\subseteq ${p[1]}" },

        Template(R.string.cat_set_logic, R.string.tmpl_union, "A \\cup B",
            listOf(Param(R.string.param_set1, "A"), Param(R.string.param_set2, "B"))) { p ->
            "${p[0]} \\cup ${p[1]}" },

        Template(R.string.cat_set_logic, R.string.tmpl_intersect, "A \\cap B",
            listOf(Param(R.string.param_set1, "A"), Param(R.string.param_set2, "B"))) { p ->
            "${p[0]} \\cap ${p[1]}" },

        Template(R.string.cat_set_logic, R.string.tmpl_forall, "\\forall x,\\, P(x)",
            listOf(Param(R.string.param_variable, "x"), Param(R.string.param_proposition, "P(x)"))) { p ->
            "\\forall ${p[0]},\\, ${p[1]}" },

        Template(R.string.cat_set_logic, R.string.tmpl_exists, "\\exists x,\\, P(x)",
            listOf(Param(R.string.param_variable, "x"), Param(R.string.param_proposition, "P(x)"))) { p ->
            "\\exists ${p[0]},\\, ${p[1]}" },
    )

    private val GREEK = listOf(
        "α" to "\\alpha",   "β" to "\\beta",      "γ" to "\\gamma",    "δ" to "\\delta",
        "ε" to "\\varepsilon","ζ" to "\\zeta",     "η" to "\\eta",      "θ" to "\\theta",
        "ι" to "\\iota",    "κ" to "\\kappa",     "λ" to "\\lambda",   "μ" to "\\mu",
        "ν" to "\\nu",      "ξ" to "\\xi",        "π" to "\\pi",       "ρ" to "\\rho",
        "σ" to "\\sigma",   "τ" to "\\tau",       "φ" to "\\varphi",   "χ" to "\\chi",
        "ψ" to "\\psi",     "ω" to "\\omega",
        "Γ" to "\\Gamma",   "Δ" to "\\Delta",     "Θ" to "\\Theta",    "Λ" to "\\Lambda",
        "Ξ" to "\\Xi",      "Π" to "\\Pi",        "Σ" to "\\Sigma",    "Φ" to "\\Phi",
        "Ψ" to "\\Psi",     "Ω" to "\\Omega",
        "∞" to "\\infty",   "∂" to "\\partial",   "∇" to "\\nabla",
        "ℏ" to "\\hbar",    "ℓ" to "\\ell",       "∅" to "\\emptyset", "ℝ" to "\\mathbb{R}",
        "ℂ" to "\\mathbb{C}","ℤ" to "\\mathbb{Z}", "ℕ" to "\\mathbb{N}","ℚ" to "\\mathbb{Q}"
    )

    fun show(context: Context, onInsert: (latex: String, isDisplay: Boolean) -> Unit, onDirect: () -> Unit) {
        showList(context, onInsert, onDirect)
    }

    private fun showList(context: Context, onInsert: (String, Boolean) -> Unit, onDirect: () -> Unit) {
        data class Item(
            val isHeader: Boolean,
            val label: String,
            val template: Template? = null,
            val isGreek: Boolean = false,
            val isDirect: Boolean = false
        )

        val items = mutableListOf<Item>()
        items += Item(false, context.getString(R.string.math_direct_input), isDirect = true)
        items += Item(true,  context.getString(R.string.math_greek_symbols))
        items += Item(false, context.getString(R.string.math_greek_list), isGreek = true)

        TEMPLATES.groupBy { it.categoryResId }.forEach { (catResId, list) ->
            items += Item(true, context.getString(catResId))
            list.forEach { t ->
                items += Item(false, "   ${context.getString(t.nameResId)}   ${t.preview}", template = t)
            }
        }

        val adapter = object : ArrayAdapter<Item>(context, 0, items) {
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val item = items[pos]
                return TextView(context).apply {
                    if (item.isHeader) {
                        text = "── ${item.label} ──"
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#1976D2"))
                        textSize = 12f
                        setPadding(32, 20, 32, 4)
                        setBackgroundColor(Color.parseColor("#F5F5F5"))
                    } else if (item.isDirect) {
                        text = item.label
                        setTextColor(Color.parseColor("#FF6F00"))
                        textSize = 15f
                        setPadding(32, 18, 32, 18)
                    } else {
                        text = item.label
                        setTextColor(Color.parseColor("#212121"))
                        textSize = 14f
                        setPadding(32, 14, 16, 14)
                    }
                }
            }
            override fun isEnabled(pos: Int) = !items[pos].isHeader
            override fun areAllItemsEnabled() = false
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.math_wizard))
            .setAdapter(adapter) { _, which ->
                val item = items[which]
                when {
                    item.isDirect  -> onDirect()
                    item.isGreek   -> showGreekDialog(context, onInsert, onDirect)
                    item.template != null -> showParamDialog(context, item.template, onInsert, onDirect)
                }
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun showParamDialog(
        context: Context,
        template: Template,
        onInsert: (String, Boolean) -> Unit,
        onDirect: () -> Unit
    ) {
        val scroll = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 24, 56, 16)
        }
        scroll.addView(layout)

        layout.addView(TextView(context).apply {
            text = context.getString(R.string.math_pattern_prefix, template.preview)
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 20)
        })

        val editTexts = template.params.map { param ->
            layout.addView(TextView(context).apply {
                text = context.getString(param.labelResId)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 10, 0, 2)
            })
            EditText(context).also { et ->
                et.setText(param.default)
                et.textSize = 15f
                layout.addView(et)
            }
        }

        layout.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).also { it.topMargin = 20; it.bottomMargin = 8 }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        })

        val previewTv = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#1976D2"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 4)
        }
        layout.addView(previewTv)

        fun currentLatex(): String {
            val vals = editTexts.mapIndexed { i, et ->
                et.text.toString().ifEmpty { template.params[i].default }
            }
            return template.build(vals)
        }
        fun refresh() { previewTv.text = "LaTeX: ${currentLatex()}" }
        refresh()

        editTexts.forEach {
            it.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = refresh()
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(template.nameResId))
            .setView(scroll)
            .setPositiveButton(context.getString(R.string.math_inline_insert)) { _, _ -> onInsert(currentLatex(), false) }
            .setNeutralButton(context.getString(R.string.math_block_insert)) { _, _ -> onInsert(currentLatex(), true) }
            .setNegativeButton(context.getString(R.string.math_back_to_list)) { _, _ -> showList(context, onInsert, onDirect) }
            .show()
    }

    private fun showGreekDialog(
        context: Context,
        onInsert: (String, Boolean) -> Unit,
        onDirect: () -> Unit
    ) {
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        val cols = 5
        GREEK.chunked(cols).forEach { row ->
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            row.forEach { (symbol, latex) ->
                Button(context).apply {
                    text = symbol
                    textSize = 18f
                    setPadding(0, 0, 0, 0)
                    minHeight = 0; minimumHeight = 0
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .also { it.setMargins(2, 2, 2, 2) }
                    setOnClickListener { onInsert(latex, false) }
                    rowLayout.addView(this)
                }
            }
            repeat(cols - row.size) {
                rowLayout.addView(android.widget.Space(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            wrapper.addView(rowLayout)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.math_greek_symbols))
            .setView(ScrollView(context).also { it.addView(wrapper) })
            .setNegativeButton(context.getString(R.string.math_back_to_list)) { _, _ -> showList(context, onInsert, onDirect) }
            .show()
    }
}
