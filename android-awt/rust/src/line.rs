use crate::fill::blend;
use jni::sys::jint;

/// Skip the line's first pixel. Polylines pass this for every segment after the first so
/// a shared vertex is plotted once — otherwise a translucent outline double-blends there.
pub const SKIP_FIRST: jint = 1;
/// Skip the last pixel (the closing segment of a polygon ends on the already-drawn start).
pub const SKIP_LAST: jint = 2;

pub fn draw_line_clipped(
    buf: &mut [i32], img_w: jint, img_h: jint,
    x0: jint, y0: jint, x1: jint, y1: jint,
    argb: jint, composite_rule: jint, rects: &[jint], flags: jint,
) {
    for r in rects.chunks_exact(4) {
        // Half-open clip rectangles; reject only lines whose bounding boxes cannot touch.
        if x0.max(x1) < r[0] || x0.min(x1) >= r[2]
            || y0.max(y1) < r[1] || y0.min(y1) >= r[3] { continue; }
        draw_line(buf, img_w, img_h, x0, y0, x1, y1, argb, composite_rule,
            (r[0], r[1], r[2], r[3]), flags);
    }
}

/// Clipped Bresenham line on an int[] buffer.
///
/// The pixel for step `i` along the major axis is computed in closed form
/// (`minor = round(i * |d_minor| / |d_major|)`), which lets the visible step range be
/// solved directly against the clip rect `[cx0, cx1) x [cy0, cy1)` instead of walking
/// every off-screen pixel: a minimap route line whose far endpoint is ten million pixels
/// away costs the same as one that ends on screen. The arithmetic for the range solve is
/// done in i128 since `2 * |d| * offset` can exceed i64 for full-range int endpoints.
///
/// Each pixel goes through the composite rule, so translucent strokes blend with what's
/// underneath (opaque SRC_OVER hits src_over's fast path).
pub fn draw_line(
    buf: &mut [i32],
    img_w: jint,
    img_h: jint,
    x0: jint,
    y0: jint,
    x1: jint,
    y1: jint,
    argb: jint,
    composite_rule: jint,
    clip: (jint, jint, jint, jint),
    flags: jint,
) {
    let cx0 = clip.0.max(0);
    let cy0 = clip.1.max(0);
    let cx1 = clip.2.min(img_w);
    let cy1 = clip.3.min(img_h);
    if cx0 >= cx1 || cy0 >= cy1 {
        return;
    }
    let dx = x1 as i64 - x0 as i64;
    let dy = y1 as i64 - y0 as i64;
    let x_major = dx.abs() >= dy.abs();
    // (a, b) = (major, minor) axis; the clip ranges are inclusive here.
    let (a0, b0, da, db, alo, ahi, blo, bhi) = if x_major {
        (x0 as i64, y0 as i64, dx, dy, cx0 as i64, cx1 as i64 - 1, cy0 as i64, cy1 as i64 - 1)
    } else {
        (y0 as i64, x0 as i64, dy, dx, cy0 as i64, cy1 as i64 - 1, cx0 as i64, cx1 as i64 - 1)
    };
    let n = da.abs();
    let adb = db.abs();
    let sa: i64 = if da >= 0 { 1 } else { -1 };
    let sb: i64 = if db >= 0 { 1 } else { -1 };

    let mut i_lo: i64 = if flags & SKIP_FIRST != 0 { 1 } else { 0 };
    let mut i_hi: i64 = if flags & SKIP_LAST != 0 { n - 1 } else { n };
    if n == 0 {
        // Single pixel: skipping either end means skipping it.
        i_hi = if flags & (SKIP_FIRST | SKIP_LAST) != 0 { -1 } else { 0 };
    }

    // Major axis: a0 + sa*i in [alo, ahi].
    let (lo, hi) = if sa > 0 { (alo - a0, ahi - a0) } else { (a0 - ahi, a0 - alo) };
    i_lo = i_lo.max(lo);
    i_hi = i_hi.min(hi);
    if i_lo > i_hi {
        return;
    }

    // Minor axis: b0 + sb*off(i) in [blo, bhi], off(i) = floor((2*i*adb + n) / (2n)).
    let (olo, ohi) = if sb > 0 { (blo - b0, bhi - b0) } else { (b0 - bhi, b0 - blo) };
    if n == 0 || adb == 0 {
        if olo > 0 || ohi < 0 {
            return;
        }
    } else {
        let two_n = 2 * n as i128;
        let two_adb = 2 * adb as i128;
        let i_min = ceil_div(two_n * olo as i128 - n as i128, two_adb);
        let i_max = ceil_div(two_n * (ohi as i128 + 1) - n as i128, two_adb) - 1;
        i_lo = (i_lo as i128).max(i_min) as i64;
        i_hi = (i_hi as i128).min(i_max) as i64;
        if i_lo > i_hi {
            return;
        }
    }

    let stride = img_w as usize;
    let two_n = 2 * n;
    let (mut off, mut rem) = if n == 0 {
        (0i64, 0i64)
    } else {
        let num = 2 * i_lo as i128 * adb as i128 + n as i128;
        ((num / two_n as i128) as i64, (num % two_n as i128) as i64)
    };
    let mut i = i_lo;
    while i <= i_hi {
        let a = a0 + sa * i;
        let b = b0 + sb * off;
        let (x, y) = if x_major { (a, b) } else { (b, a) };
        // The range solve already keeps us inside the clip; this guard is what keeps an
        // arithmetic slip from becoming an out-of-bounds index (panic = abort).
        if x >= cx0 as i64 && x < cx1 as i64 && y >= cy0 as i64 && y < cy1 as i64 {
            let idx = y as usize * stride + x as usize;
            blend(buf, idx, argb, composite_rule);
        }
        if n > 0 {
            rem += 2 * adb;
            if rem >= two_n {
                rem -= two_n;
                off += 1;
            }
        }
        i += 1;
    }
}

#[inline]
fn ceil_div(a: i128, b: i128) -> i128 {
    let q = a.div_euclid(b);
    if a.rem_euclid(b) != 0 { q + 1 } else { q }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::SRC;

    const C: i32 = 0xFF00FF00u32 as i32;

    fn count(buf: &[i32]) -> usize {
        buf.iter().filter(|&&p| p == C).count()
    }

    fn reference(x0: i32, y0: i32, x1: i32, y1: i32) -> Vec<(i32, i32)> {
        let mut buf = vec![0i32; 64 * 64];
        draw_line(&mut buf, 64, 64, x0, y0, x1, y1, C, SRC, (0, 0, 64, 64), 0);
        let mut v = Vec::new();
        for y in 0..64 {
            for x in 0..64 {
                if buf[(y * 64 + x) as usize] == C {
                    v.push((x, y));
                }
            }
        }
        v
    }

    #[test]
    fn unclipped_lines_are_connected_and_hit_endpoints() {
        for &(x0, y0, x1, y1) in &[(1, 1, 60, 20), (60, 5, 3, 50), (10, 60, 12, 2), (5, 5, 5, 40), (2, 30, 61, 30), (7, 7, 7, 7)] {
            let px = reference(x0, y0, x1, y1);
            assert!(px.contains(&(x0, y0)) && px.contains(&(x1, y1)));
            let n = (x1 - x0).abs().max((y1 - y0).abs()) as usize + 1;
            assert_eq!(px.len(), n);
        }
    }

    #[test]
    fn clipping_matches_unclipped_pixels_inside_clip() {
        let lines = [(-50, -20, 90, 70), (100, 3, -40, 60), (10, -100, 50, 200), (-5, 40, 70, 41)];
        for &(x0, y0, x1, y1) in &lines {
            let mut full = vec![0i32; 64 * 64];
            // Same line drawn into a big buffer offset by 200 so nothing is clipped.
            let mut big = vec![0i32; 500 * 500];
            draw_line(&mut big, 500, 500, x0 + 200, y0 + 200, x1 + 200, y1 + 200, C, SRC, (0, 0, 500, 500), 0);
            for y in 0..64 {
                for x in 0..64 {
                    full[(y * 64 + x) as usize] = big[((y + 200) * 500 + x + 200) as usize];
                }
            }
            let mut clipped = vec![0i32; 64 * 64];
            draw_line(&mut clipped, 64, 64, x0, y0, x1, y1, C, SRC, (10, 12, 50, 47), 0);
            for y in 0..64 {
                for x in 0..64 {
                    let inside = x >= 10 && x < 50 && y >= 12 && y < 47;
                    let want = if inside { full[(y * 64 + x) as usize] } else { 0 };
                    assert_eq!(clipped[(y * 64 + x) as usize], want, "line {:?} at {},{}", (x0, y0, x1, y1), x, y);
                }
            }
        }
    }

    #[test]
    fn far_endpoints_are_cheap_and_safe() {
        let mut buf = vec![0i32; 64 * 64];
        draw_line(&mut buf, 64, 64, 30, 30, 10_000_000, 5_000_000, C, SRC, (0, 0, 64, 64), 0);
        assert!(count(&buf) > 20);
        draw_line(&mut buf, 64, 64, i32::MIN, i32::MIN, i32::MAX, i32::MAX, C, SRC, (0, 0, 64, 64), 0);
        draw_line(&mut buf, 64, 64, i32::MAX, i32::MIN, i32::MIN, i32::MAX, C, SRC, (-5, -5, 100, 100), 0);
    }

    #[test]
    fn skip_flags_drop_endpoints() {
        let mut buf = vec![0i32; 64 * 64];
        draw_line(&mut buf, 64, 64, 0, 0, 10, 0, C, SRC, (0, 0, 64, 64), SKIP_FIRST | SKIP_LAST);
        assert_eq!(count(&buf), 9);
        assert_eq!(buf[0], 0);
        assert_eq!(buf[10], 0);
    }

    #[test]
    fn batched_clip_matches_separate_draws() {
        let mut rects = Vec::new();
        for y in 0..64 {
            let inset = (y - 32i32).abs() / 2;
            rects.extend_from_slice(&[inset, y, 64 - inset, y + 1]);
        }
        let lines = [(-50, 0, 100, 63), (63, 0, 0, 63), (0, 32, 63, 32),
            (32, -100, 32, 100), (20, 20, 20, 20),
            (i32::MIN, i32::MIN, i32::MAX, i32::MAX), (90, 90, 100, 100)];
        for rule in 1..=12 {
            for flags in 0..=3 {
                for &(x0, y0, x1, y1) in &lines {
                    let mut reference = vec![0x40302010; 64 * 64];
                    let mut batched = reference.clone();
                    for r in rects.chunks_exact(4) {
                        draw_line(&mut reference, 64, 64, x0, y0, x1, y1,
                            0x80804020u32 as i32, rule, (r[0], r[1], r[2], r[3]), flags);
                    }
                    draw_line_clipped(&mut batched, 64, 64, x0, y0, x1, y1,
                        0x80804020u32 as i32, rule, &rects, flags);
                    assert_eq!(reference, batched, "rule={rule} flags={flags} line={:?}", (x0,y0,x1,y1));
                }
            }
        }
    }
}
