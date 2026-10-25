//! runelite-awt-native — JNI side of the AWT compatibility shim.
//!
//! Every entry point declared in `net.runelite.awt.AwtNative` lives here. Pixel buffers
//! arrive as `int[]` in canonical ARGB packing (`0xAARRGGBB`) and are pinned for the
//! duration of one call via the JNI critical-section API for zero-copy access.

use jni::objects::{JClass, JDoubleArray, JIntArray, JString, ReleaseMode};
use jni::sys::{jboolean, jfloat, jint};
use jni::JNIEnv;

mod blit;
mod fill;
mod line;
mod poly;

#[cfg(target_os = "android")]
fn init_android_log() {
    use android_logger::Config;
    use log::LevelFilter;
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("runelite-awt"),
    );
}

#[cfg(not(target_os = "android"))]
fn init_android_log() {}

#[no_mangle]
pub extern "system" fn Java_net_runelite_awt_AwtCompat_setNativeEnv<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    key: JString<'local>,
    value: JString<'local>,
) -> jint {
    let key_str: String = match env.get_string(&key) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };
    let value_str: String = match env.get_string(&value) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };

    #[cfg(unix)]
    {
        let c_key = match std::ffi::CString::new(key_str) {
            Ok(c) => c,
            Err(_) => return -1,
        };
        let c_val = match std::ffi::CString::new(value_str) {
            Ok(c) => c,
            Err(_) => return -1,
        };
        // Safety: setenv changes the global environment array which is not thread-safe.
        // Call this as early as possible during application startup.
        unsafe { libc::setenv(c_key.as_ptr(), c_val.as_ptr(), 1) }
    }
    #[cfg(not(unix))]
    {
        std::env::set_var(key_str, value_str);
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_net_runelite_awt_AwtCompat_nativeAbiVersion<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jint {
    init_android_log();
    log::info!("runelite-awt-native loaded");
    NATIVE_ABI_VERSION
}

const NATIVE_ABI_VERSION: jint = 1;

// ---------- AlphaComposite rules (kept in sync with java.awt.AlphaComposite) ----------
pub const CLEAR: jint = 1;
pub const SRC: jint = 2;
pub const SRC_OVER: jint = 3;
pub const DST_OVER: jint = 4;
pub const SRC_IN: jint = 5;
pub const DST_IN: jint = 6;
pub const SRC_OUT: jint = 7;
pub const DST_OUT: jint = 8;
pub const DST: jint = 9;
pub const SRC_ATOP: jint = 10;
pub const DST_ATOP: jint = 11;
pub const XOR: jint = 12;

/// Pin an int[] for the duration of a closure. The array is released back to the JVM
/// when the wrapper drops; we always commit so the caller sees mutations.
///
/// `GetPrimitiveArrayCritical`, not `GetIntArrayElements`. ART implements the latter as
/// an unconditional `new[]` + memcpy in, and a memcpy back + `delete[]` on release, for
/// every array small enough to live in a movable space (under the 12 KB large-object
/// threshold). That is every glyph bitmap the text cache blits, every icon, and every
/// shape-rasterizer scratch buffer — hundreds of native allocations and round-trip copies
/// per painted frame, for draws that touch a handful of pixels. The critical variant hands
/// back the real backing store instead. Buffers big enough to be large objects were already
/// non-movable and so already copy-free; they are unaffected either way.
///
/// The tradeoff is that a moving GC cannot run while the pointer is held, so nothing
/// between acquire and release may call back into the JVM or block. The closures here are
/// pure pixel arithmetic, and any JNI work the callers need (array lengths, `is_same_object`,
/// pinning the small polygon coordinate arrays) is done before the critical is taken.
fn with_pinned_array<F, R>(
    env: &mut JNIEnv<'_>,
    array: &JIntArray<'_>,
    mode: ReleaseMode,
    f: F,
) -> Option<R>
where
    F: FnOnce(&mut [i32]) -> R,
{
    let len = env.get_array_length(array).ok()?;
    if len <= 0 {
        return None;
    }
    let elements = unsafe { env.get_array_elements_critical(array, mode).ok()? };
    let slice = unsafe { std::slice::from_raw_parts_mut(elements.as_ptr(), elements.len()) };
    Some(f(slice))
}

/// Pin a read-only source and a writable destination int[] simultaneously for one
/// closure. The source is released `NoCopyBack` (we never mutate it, so there is
/// nothing to commit); the destination is committed `CopyBack`.
///
/// Both use `GetPrimitiveArrayCritical` — nesting two critical sections is explicitly
/// allowed by the JNI spec as long as they are released in reverse order, which the drop
/// order of the two locals below gives us. The second `JNIEnv` is an `unsafe_clone` purely
/// to get past the borrow checker: `get_array_elements_critical` takes `&mut self` for the
/// lifetime of the guard, so one handle cannot hold two. Neither handle creates a local
/// reference, which is the thing `unsafe_clone` is actually unsound for.
///
/// SAFETY: the caller must ensure `src` and `dst` are *distinct* Java arrays. Pinning
/// the same backing store as both `&[i32]` and `&mut [i32]` aliases it, which is UB —
/// `Java_..._blit` guards this with `is_same_object` and falls back to a copy.
fn with_src_dst_pinned<F, R>(
    env: &mut JNIEnv<'_>,
    src: &JIntArray<'_>,
    dst: &JIntArray<'_>,
    f: F,
) -> Option<R>
where
    F: FnOnce(&[i32], &mut [i32]) -> R,
{
    if env.get_array_length(src).ok()? <= 0 || env.get_array_length(dst).ok()? <= 0 {
        return None;
    }
    let mut env2 = unsafe { env.unsafe_clone() };
    let dst_elems = unsafe { env.get_array_elements_critical(dst, ReleaseMode::CopyBack).ok()? };
    let src_elems = unsafe {
        env2.get_array_elements_critical(src, ReleaseMode::NoCopyBack)
            .ok()?
    };
    let src_slice = unsafe { std::slice::from_raw_parts(src_elems.as_ptr(), src_elems.len()) };
    let dst_slice = unsafe { std::slice::from_raw_parts_mut(dst_elems.as_ptr(), dst_elems.len()) };
    let r = f(src_slice, dst_slice);
    // Explicit, so the reverse-order release the JNI spec asks for is not left to an
    // accident of declaration order.
    drop(src_elems);
    drop(dst_elems);
    Some(r)
}

#[no_mangle]
pub extern "system" fn Java_net_runelite_awt_AwtNative_fillRect<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JIntArray<'local>,
    img_w: jint,
    img_h: jint,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    argb: jint,
    composite_rule: jint,
) {
    let _ = with_pinned_array(&mut env, &pixels, ReleaseMode::CopyBack, |buf| {
        fill::fill_rect(buf, img_w, img_h, x, y, w, h, argb, composite_rule);
    });
}

#[no_mangle]
pub extern "system" fn Java_net_runelite_awt_AwtNative_fillOval<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JIntArray<'local>,
    img_w: jint,
    img_h: jint,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    argb: jint,
    composite_rule: jint,
) {
    let _ = with_pinned_array(&mut env, &pixels, ReleaseMode::CopyBack, |buf| {
        fill::fill_oval(buf, img_w, img_h, x, y, w, h, argb, composite_rule);
    });
}

#[no_mangle]
pub extern "system" fn Java_net_runelite_awt_AwtNative_drawOval<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JIntArray<'local>,
    img_w: jint,
    img_h: jint,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    argb: jint,
) {
    let _ = with_pinned_array(&mut env, &pixels, ReleaseMode::CopyBack, |buf| {
        fill::draw_oval(buf, img_w, img_h, x, y, w, h, argb);
    });
}

#[no_mangle]
pub extern "system" fn Java_net_runelite_awt_AwtNative_fillRoundRect<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JIntArray<'local>,
    img_w: jint,
    img_h: jint,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    arc_w: jint,
    arc_h: jint,
    argb: jint,
    composite_rule: jint,
) {
    let _ = with_pinned_array(&mut env, &pixels, ReleaseMode::CopyBack, |buf| {
        fill::fill_round_rect(buf, img_w, img_h, x, y, w, h, arc_w, arc_h, argb, composite_rule);
    });
}

#[no_mangle]
pub extern "system" fn Java_net_runelite_awt_AwtNative_drawLine<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JIntArray<'local>,
    img_w: jint,
    img_h: jint,
    x0: jint,
    y0: jint,
    x1: jint,
    y1: jint,
    argb: jint,
    composite_rule: jint,
    clip_x0: jint,
    clip_y0: jint,
    clip_x1: jint,
    clip_y1: jint,
    flags: jint,
) {
    let _ = with_pinned_array(&mut env, &pixels, ReleaseMode::CopyBack, |buf| {
        line::draw_line(
            buf, img_w, img_h, x0, y0, x1, y1, argb, composite_rule,
            (clip_x0, clip_y0, clip_x1, clip_y1), flags,
        );
    });
}

#[no_mangle]
pub extern "system" fn Java_net_runelite_awt_AwtNative_fillPath<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JIntArray<'local>,
    img_w: jint,
    img_h: jint,
    coords: JDoubleArray<'local>,
    counts: JIntArray<'local>,
    n_contours: jint,
    even_odd: jboolean,
    clip: JIntArray<'local>,
    n_clip: jint,
    argb: jint,
    composite_rule: jint,
) {
    if n_contours <= 0 || n_clip <= 0 {
        return;
    }
    // Regular (non-critical) pinning for the coordinate / clip arrays, and acquired
    // *before* with_pinned_array takes its critical section: these are JNI calls, which
    // are not allowed while a critical is held. They are small, so the copy ART may make
    // here is irrelevant.
    let coords_e = unsafe { env.get_array_elements(&coords, ReleaseMode::NoCopyBack).ok() };
    let counts_e = unsafe { env.get_array_elements(&counts, ReleaseMode::NoCopyBack).ok() };
    let clip_e = unsafe { env.get_array_elements(&clip, ReleaseMode::NoCopyBack).ok() };
    if let (Some(co), Some(cn), Some(cl)) = (coords_e, counts_e, clip_e) {
        let co = unsafe { std::slice::from_raw_parts(co.as_ptr(), co.len()) };
        let cn = unsafe { std::slice::from_raw_parts(cn.as_ptr(), cn.len()) };
        let cl = unsafe { std::slice::from_raw_parts(cl.as_ptr(), cl.len()) };
        let cn = &cn[..(n_contours as usize).min(cn.len())];
        let cl = &cl[..((n_clip as usize) * 4).min(cl.len() / 4 * 4)];
        let _ = with_pinned_array(&mut env, &pixels, ReleaseMode::CopyBack, |buf| {
            poly::fill_path(buf, img_w, img_h, co, cn, even_odd != 0, cl, argb, composite_rule);
        });
    }
}

#[no_mangle]
pub extern "system" fn Java_net_runelite_awt_AwtNative_copyArea<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JIntArray<'local>,
    img_w: jint,
    img_h: jint,
    x: jint,
    y: jint,
    w: jint,
    h: jint,
    dst_x: jint,
    dst_y: jint,
) {
    let _ = with_pinned_array(&mut env, &pixels, ReleaseMode::CopyBack, |buf| {
        blit::copy_area(buf, img_w, img_h, x, y, w, h, dst_x, dst_y);
    });
}

#[no_mangle]
pub extern "system" fn Java_net_runelite_awt_AwtNative_blit<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    src: JIntArray<'local>,
    src_w: jint,
    src_h: jint,
    sx: jint,
    sy: jint,
    sw: jint,
    sh: jint,
    dst: JIntArray<'local>,
    dst_w: jint,
    dst_h: jint,
    dx: jint,
    dy: jint,
    dw: jint,
    dh: jint,
    composite_rule: jint,
    alpha: jfloat,
) {
    let distinct = !env.is_same_object(&src, &dst).unwrap_or(true);
    if distinct {
        let _ = with_src_dst_pinned(&mut env, &src, &dst, |src_buf, dst_buf| {
            blit::blit(
                src_buf, src_w, src_h, sx, sy, sw, sh,
                dst_buf, dst_w, dst_h, dx, dy, dw, dh,
                composite_rule, alpha,
            );
        });
        return;
    }

    let Some(src_vec) = read_int_array(&mut env, &src) else { return };
    let _ = with_pinned_array(&mut env, &dst, ReleaseMode::CopyBack, |dst_buf| {
        blit::blit(
            &src_vec, src_w, src_h, sx, sy, sw, sh,
            dst_buf, dst_w, dst_h, dx, dy, dw, dh,
            composite_rule, alpha,
        );
    });
}

fn read_int_array(env: &mut JNIEnv<'_>, array: &JIntArray<'_>) -> Option<Vec<i32>> {
    let len = env.get_array_length(array).ok()?;
    if len <= 0 {
        return Some(Vec::new());
    }
    let mut out = vec![0i32; len as usize];
    env.get_int_array_region(array, 0, &mut out).ok()?;
    Some(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_fill_rect_and_oval() {
        let mut buf = vec![0i32; 100 * 100];
        fill::fill_rect(&mut buf, 100, 100, 10, 10, 20, 20, 0xFF112233u32 as i32, SRC);
        assert_eq!(buf[10 * 100 + 10], 0xFF112233u32 as i32);
        assert_eq!(buf[29 * 100 + 29], 0xFF112233u32 as i32);
        assert_eq!(buf[30 * 100 + 30], 0);

        fill::fill_oval(&mut buf, 100, 100, 40, 40, 20, 20, 0xFF445566u32 as i32, SRC);
        // Center of circle at (50, 50)
        assert_eq!(buf[50 * 100 + 50], 0xFF445566u32 as i32);
    }

    #[test]
    fn test_fill_path_square() {
        let mut buf = vec![0i32; 100 * 100];
        let pts = [10.0, 10.0, 30.0, 10.0, 30.0, 30.0, 10.0, 30.0];
        poly::fill_path(&mut buf, 100, 100, &pts, &[4], true, &[0, 0, 100, 100], 0xFFAABBCCu32 as i32, SRC);
        assert_eq!(buf[15 * 100 + 15], 0xFFAABBCCu32 as i32);
        assert_eq!(buf[5 * 100 + 5], 0);
    }

    #[test]
    fn test_copy_area_no_alloc() {
        let mut buf = vec![0i32; 100 * 100];
        buf[10 * 100 + 10] = 0x12345678;
        blit::copy_area(&mut buf, 100, 100, 10, 10, 5, 5, 20, 20);
        assert_eq!(buf[20 * 100 + 20], 0x12345678);
    }
}

