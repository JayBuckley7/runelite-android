use crate::fill::plot_span;
use jni::sys::jint;

struct Edge {
    /// Top / bottom y (top < bottom) and the x at the top.
    y_top: f64,
    y_bot: f64,
    x_top: f64,
    dxdy: f64,
    /// +1 for a downward edge, -1 for upward: the winding contribution.
    dir: i32,
    /// First row whose centre is on the edge, and one past the last.
    row0: i32,
    row1: i32,
}

/// Scanline fill for a multi-contour path in device space.
///
/// `coords` holds x,y pairs; `counts[i]` is the number of vertices in contour `i` (each
/// contour is implicitly closed). Pixels are filled when their centre is inside the path
/// under the even-odd or non-zero rule — the same sampling as Java2D's non-AA fill — so a
/// set of overlapping stroke pieces can be filled in one non-zero pass and translucent
/// colours don't double-blend where pieces overlap.
///
/// `clip` is a list of disjoint device rects as `(x0, y0, x1, y1)` quads (half-open);
/// every span is intersected with them, which is how rectangle-list (non-rectangular)
/// clips reach the native fill. The row range is restricted to the clip up front, and
/// crossings are evaluated in f64, so huge off-screen coordinates cost nothing extra.
pub fn fill_path(
    buf: &mut [i32],
    img_w: jint,
    img_h: jint,
    coords: &[f64],
    counts: &[i32],
    even_odd: bool,
    clip: &[i32],
    argb: jint,
    composite_rule: jint,
) {
    // Clip extent, intersected with the image.
    let mut cy0 = i32::MAX;
    let mut cy1 = i32::MIN;
    let mut rects = 0usize;
    for r in clip.chunks_exact(4) {
        if r[0] >= r[2] || r[1] >= r[3] {
            continue;
        }
        cy0 = cy0.min(r[1]);
        cy1 = cy1.max(r[3]);
        rects += 1;
    }
    if rects == 0 {
        return;
    }
    let cy0 = cy0.max(0);
    let cy1 = cy1.min(img_h);
    if cy0 >= cy1 {
        return;
    }

    let mut edges: Vec<Edge> = Vec::new();
    let mut base = 0usize;
    for &count in counts {
        if count <= 0 {
            continue;
        }
        let count = count as usize;
        if (base + count) * 2 > coords.len() {
            break;
        }
        for i in 0..count {
            let j = if i + 1 == count { 0 } else { i + 1 };
            let (xa, ya) = (coords[(base + i) * 2], coords[(base + i) * 2 + 1]);
            let (xb, yb) = (coords[(base + j) * 2], coords[(base + j) * 2 + 1]);
            if !(xa.is_finite() && ya.is_finite() && xb.is_finite() && yb.is_finite()) || ya == yb {
                continue;
            }
            let (dir, xt, yt, xbt, ybt) = if ya < yb { (1, xa, ya, xb, yb) } else { (-1, xb, yb, xa, ya) };
            // Rows whose centre y+0.5 lies in [y_top, y_bot).
            let row0 = clamp_row((yt - 0.5).ceil()).max(cy0);
            let row1 = clamp_row((ybt - 0.5).ceil()).min(cy1);
            if row0 >= row1 {
                continue;
            }
            edges.push(Edge {
                y_top: yt,
                y_bot: ybt,
                x_top: xt,
                dxdy: (xbt - xt) / (ybt - yt),
                dir,
                row0,
                row1,
            });
        }
        base += count;
    }
    if edges.is_empty() {
        return;
    }
    edges.sort_unstable_by_key(|e| e.row0);
    let row_start = edges[0].row0;
    let row_end = edges.iter().map(|e| e.row1).max().unwrap_or(row_start);

    let stride = img_w as usize;
    let mut active: Vec<usize> = Vec::new();
    let mut crossings: Vec<(f64, i32)> = Vec::new();
    let mut next = 0usize;
    for row in row_start..row_end {
        while next < edges.len() && edges[next].row0 <= row {
            active.push(next);
            next += 1;
        }
        active.retain(|&k| edges[k].row1 > row);
        if active.is_empty() {
            continue;
        }
        let yc = row as f64 + 0.5;
        crossings.clear();
        for &k in &active {
            let e = &edges[k];
            debug_assert!(yc >= e.y_top && yc < e.y_bot);
            crossings.push((e.x_top + (yc - e.y_top) * e.dxdy, e.dir));
        }
        crossings.sort_unstable_by(|a, b| a.0.partial_cmp(&b.0).unwrap_or(std::cmp::Ordering::Equal));

        let mut winding = 0i32;
        let mut span_start = 0f64;
        for &(x, dir) in &crossings {
            let was_inside = inside(winding, even_odd);
            winding += dir;
            let now_inside = inside(winding, even_odd);
            if !was_inside && now_inside {
                span_start = x;
            } else if was_inside && !now_inside {
                // Columns whose centre x+0.5 lies in [span_start, x).
                let c0 = clamp_col((span_start - 0.5).ceil());
                let c1 = clamp_col((x - 0.5).ceil());
                if c0 < c1 {
                    for r in clip.chunks_exact(4) {
                        if row >= r[1] && row < r[3] {
                            let a = c0.max(r[0]);
                            let b = c1.min(r[2]);
                            if a < b {
                                plot_span(buf, stride, row, a, b, img_w, img_h, argb, composite_rule);
                            }
                        }
                    }
                }
            }
        }
    }
}

#[inline]
fn inside(winding: i32, even_odd: bool) -> bool {
    if even_odd { winding & 1 != 0 } else { winding != 0 }
}

#[inline]
fn clamp_row(v: f64) -> i32 {
    v.max(-1.0e9).min(1.0e9) as i32
}

#[inline]
fn clamp_col(v: f64) -> i32 {
    v.max(-1.0e9).min(1.0e9) as i32
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{SRC, SRC_OVER};

    const C: i32 = 0xFFAABBCCu32 as i32;
    const FULL: [i32; 4] = [0, 0, 100, 100];

    #[test]
    fn integer_square_fills_exact_pixels() {
        let mut buf = vec![0i32; 100 * 100];
        let pts = [10.0, 10.0, 30.0, 10.0, 30.0, 30.0, 10.0, 30.0];
        fill_path(&mut buf, 100, 100, &pts, &[4], true, &FULL, C, SRC);
        assert_eq!(buf[10 * 100 + 10], C);
        assert_eq!(buf[29 * 100 + 29], C);
        assert_eq!(buf[30 * 100 + 29], 0);
        assert_eq!(buf[29 * 100 + 30], 0);
        assert_eq!(buf[9 * 100 + 10], 0);
        assert_eq!(buf.iter().filter(|&&p| p == C).count(), 400);
    }

    #[test]
    fn nonzero_union_does_not_double_blend() {
        // Two overlapping squares, same orientation, translucent: the overlap must get
        // exactly one blend under non-zero.
        let mut buf = vec![0xFF000000u32 as i32; 100 * 100];
        let pts = [0.0, 0.0, 20.0, 0.0, 20.0, 20.0, 0.0, 20.0, 10.0, 10.0, 30.0, 10.0, 30.0, 30.0, 10.0, 30.0];
        let c = 0x80FFFFFFu32 as i32;
        fill_path(&mut buf, 100, 100, &pts, &[4, 4], false, &FULL, c, SRC_OVER);
        assert_eq!(buf[5 * 100 + 5], buf[15 * 100 + 15]);
        // Even-odd leaves the overlap as a hole.
        let mut buf2 = vec![0i32; 100 * 100];
        fill_path(&mut buf2, 100, 100, &pts, &[4, 4], true, &FULL, C, SRC);
        assert_eq!(buf2[15 * 100 + 15], 0);
        assert_eq!(buf2[5 * 100 + 5], C);
    }

    #[test]
    fn clip_rect_list_restricts_spans() {
        let mut buf = vec![0i32; 100 * 100];
        let pts = [0.0, 0.0, 100.0, 0.0, 100.0, 100.0, 0.0, 100.0];
        // Rect-minus-rect as two disjoint rects.
        let clip = [10, 10, 50, 20, 10, 20, 20, 50];
        fill_path(&mut buf, 100, 100, &pts, &[4], true, &clip, C, SRC);
        assert_eq!(buf.iter().filter(|&&p| p == C).count(), 40 * 10 + 10 * 30);
        assert_eq!(buf[30 * 100 + 30], 0);
        assert_eq!(buf[15 * 100 + 45], C);
    }

    #[test]
    fn huge_coordinates_are_safe() {
        let mut buf = vec![0i32; 100 * 100];
        let pts = [50.0, 50.0, 1.0e7, 5.0e6, 1.0e7, 5.0e6 + 3.0, 50.0, 53.0, -1.0e300, f64::NAN];
        fill_path(&mut buf, 100, 100, &pts, &[5], false, &FULL, C, SRC);
        assert!(buf.iter().filter(|&&p| p == C).count() > 50);
        // Malformed counts must not index out of range.
        fill_path(&mut buf, 100, 100, &pts, &[40], false, &FULL, C, SRC);
    }
}
