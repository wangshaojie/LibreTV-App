package com.wangshaojie.libretv;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 自绘对话框。
 *
 * 为什么不用系统 AlertDialog：
 *
 *  1. 焦点错位。系统对话框在电视上会把焦点默认放在「确定 / 退出」这类正向按钮上，
 *     遥控器按一次 OK 就直接退出应用，用户根本没看清弹的是什么 —— 这是最危险的一种默认。
 *     这里所有弹框都把焦点交给安全选项（继续浏览 / 取消），破坏性操作必须主动选。
 *
 *  2. 样式脱节。系统对话框是 Material 蓝白配色、系统字体、硬边直角，
 *     和应用的界面完全不是一套东西，放在自绘界面里非常突兀。
 *     这里面板、按钮、焦点态全部走 Ui 的同一套 token，弹框和界面是一个产品。
 *
 *  3. 列表不可控。系统的 setItems 列表在电视上用默认行高和默认选中样式，
 *     焦点落上去没有品牌化的反馈。这里每行都是自定义行，焦点态清晰可见。
 */
final class UiDialog {

    interface OnPick {
        void pick(int index);
    }

    /** 表单的确认动作：返回 false 表示校验没过，弹框不关闭 */
    interface OnConfirm {
        boolean run();
    }

    /** 确认类弹框：取消在左（默认聚焦），确认在右。 */
    static Dialog confirm(Activity a, String title, String message, String cancelLabel, String confirmLabel, Runnable onConfirm) {
        LinearLayout body = shell(a, title);
        if (message != null && !message.isEmpty()) body.addView(Ui.label(a, message, 14, Ui.MUTED));
        body.addView(Ui.gap(a, 20));
        final Dialog[] holder = new Dialog[1];
        Button cancel = Ui.outline(a, cancelLabel, () -> {});
        Button confirm = Ui.primary(a, confirmLabel, () -> {});
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(cancel);
        row.addView(confirm);
        body.addView(row);
        holder[0] = present(a, body);
        // 焦点落在「取消 / 继续浏览」：破坏性操作必须由用户主动选，不能一按 OK 就中招
        cancel.requestFocus();
        cancel.setOnClickListener(v -> holder[0].dismiss());
        confirm.setOnClickListener(v -> { holder[0].dismiss(); if (onConfirm != null) onConfirm.run(); });
        return holder[0];
    }

    /** 只有「知道了」的提示弹框。 */
    static Dialog message(Activity a, String title, String text) {
        LinearLayout body = shell(a, title);
        if (text != null && !text.isEmpty()) body.addView(Ui.label(a, text, 14, Ui.MUTED));
        body.addView(Ui.gap(a, 20));
        final Dialog[] holder = new Dialog[1];
        Button ok = Ui.primary(a, "知道了", () -> {});
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(ok);
        body.addView(row);
        holder[0] = present(a, body);
        ok.requestFocus();
        ok.setOnClickListener(v -> holder[0].dismiss());
        return holder[0];
    }

    /**
     * 列表弹框。
     *
     * checked >= 0 时在选中项前画一个实心圆点，用于单选场景（多行 radio）。
     * 行本身可聚焦，焦点态用 Ui.focus()，遥控器上下键有明显反馈。
     */
    static Dialog list(Activity a, String title, String[] items, int checked, OnPick onPick) {
        LinearLayout body = shell(a, title);
        final Dialog[] holder = new Dialog[1];
        LinearLayout column = Ui.column(a);
        for (int n = 0; n < items.length; n++) {
            final int index = n;
            LinearLayout row = Ui.row(a);
            row.setBackground(Ui.focus());
            row.setFocusable(true);
            row.setClickable(true);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(a, 16), Ui.dp(a, 13), Ui.dp(a, 16), Ui.dp(a, 13));
            if (n == checked) {
                TextView dot = Ui.label(a, "●", 13, Ui.ACCENT);
                dot.setGravity(Gravity.CENTER);
                row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 20)));
            } else {
                View pad = new View(a);
                row.addView(pad, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 20)));
            }
            TextView label = Ui.label(a, items[n], 15, Ui.TEXT);
            label.setSingleLine(true);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, Ui.dp(a, 8));
            column.addView(row, lp);
            row.setOnClickListener(v -> { if (onPick != null) onPick.pick(index); holder[0].dismiss(); });
        }
        if (items.length > 6) {
            ScrollView scroll = new ScrollView(a);
            scroll.addView(column);
            body.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        } else {
            body.addView(column, new LinearLayout.LayoutParams(-1, -2));
        }
        body.addView(Ui.gap(a, 16));
        final Button close = Ui.outline(a, "关闭", () -> {});
        LinearLayout buttons = Ui.row(a);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttons.addView(close);
        body.addView(buttons);
        holder[0] = present(a, body);
        close.requestFocus();
        close.setOnClickListener(v -> holder[0].dismiss());
        return holder[0];
    }

    /** 表单弹框：内容自定义，取消在左（默认聚焦），保存/确定在右。校验没过时不关闭。 */
    static Dialog form(Activity a, String title, View content, String cancelLabel, String confirmLabel, OnConfirm onConfirm) {
        LinearLayout body = shell(a, title);
        body.addView(content, new LinearLayout.LayoutParams(-1, -2));
        body.addView(Ui.gap(a, 18));
        final Dialog[] holder = new Dialog[1];
        Button cancel = Ui.outline(a, cancelLabel, () -> {});
        Button confirm = Ui.primary(a, confirmLabel, () -> {});
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(cancel);
        row.addView(confirm);
        body.addView(row);
        holder[0] = present(a, body);
        cancel.requestFocus();
        cancel.setOnClickListener(v -> holder[0].dismiss());
        confirm.setOnClickListener(v -> { if (onConfirm == null || onConfirm.run()) holder[0].dismiss(); });
        return holder[0];
    }

    /** 带自定义内容和一个附加动作的弹框（例如隐私说明 → 查看完整许可）。 */
    static Dialog custom(Activity a, String title, View content, String neutralLabel, Runnable onNeutral) {
        LinearLayout body = shell(a, title);
        body.addView(content, new LinearLayout.LayoutParams(-1, -2));
        body.addView(Ui.gap(a, 18));
        final Dialog[] holder = new Dialog[1];
        Button neutral = Ui.outline(a, neutralLabel == null ? "关闭" : neutralLabel, () -> {});
        Button close = Ui.outline(a, "关闭", () -> {});
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (neutralLabel != null) row.addView(neutral);
        row.addView(close);
        body.addView(row);
        holder[0] = present(a, body);
        (neutralLabel == null ? close : neutral).requestFocus();
        close.setOnClickListener(v -> holder[0].dismiss());
        if (neutralLabel != null) neutral.setOnClickListener(v -> { holder[0].dismiss(); if (onNeutral != null) onNeutral.run(); });
        return holder[0];
    }

    /** 弹框骨架：标题 + 内容容器 */
    private static LinearLayout shell(Activity a, String title) {
        LinearLayout body = Ui.column(a);
        body.setBackground(panel());
        body.setPadding(Ui.dp(a, 26), Ui.dp(a, 24), Ui.dp(a, 26), Ui.dp(a, 22));
        if (title != null && !title.isEmpty()) {
            TextView heading = Ui.label(a, title, 19, Ui.TEXT);
            heading.setTypeface(null, Typeface.BOLD);
            body.addView(heading);
            body.addView(Ui.gap(a, 10));
        }
        return body;
    }

    /** 弹出并按屏幕宽度定尺寸：电视上占屏宽约 40%，手机约 88%。 */
    private static Dialog present(Activity a, View body) {
        Dialog dialog = new Dialog(a, 0);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(body);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.62f);
        }
        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w == null) return;
            int screen = a.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(screen - Ui.dp(a, 48), Ui.dp(a, Ui.tv(a) ? 660 : 420));
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        });
        dialog.show();
        return dialog;
    }

    private static GradientDrawable panel() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Ui.SURFACE);
        float density = Resources.getSystem().getDisplayMetrics().density;
        d.setCornerRadius(20 * density);
        d.setStroke(Math.max(2, Math.round(1f * density)), Ui.LINE);
        return d;
    }
}
