# Dark acrylic UI — Compose design spec                                                                                             This spec translates the observed visual system into reusable Kotlin + Jetpack Compose guidance. It favors the app’s actual **tinted-surface acrylic** treatment; the checked surface uses no backdrop blur. Disabled appearances below are implementation guidance where the decompiled UI did not expose a distinct disabled design.                                                                      ## 1. Style and feel
Use a near-black canvas, compact dark surfaces, cool blue accents, and restrained icy-blue translucency. The result should feel like a precise icon-editing tool, not a fully blurred “glass” interface: panels are readable and mostly opaque, separation comes from a subtle blue edge and soft shadow, and color is concentrated in controls and small ambient highlights.                                                                                                 ## 2. Tokens
                                                                  | Token | Value | Use |                                           |---|---:|---|                                                    | Canvas | `#000000` | App background |                           | Surface | `#13161B` | Default card/control surface |
| Surface pressed | `#292D35` | Pressed card surface |            | Surface raised | `#17181C` | Secondary raised surface |         | Text primary | `#FFFFFF` | Headings and primary labels |        | Text secondary | `#80C4D2EF` | Secondary copy (50% alpha) |
| Action blue | `#006ED5` | Primary action |
| Accent blue | `#317AFF` | Secondary accent/selection |
| Accent light | `#2B8EFE` | Highlight |
| Accent pressed | `#1882FB` | Pressed primary action |
| Acrylic tint/border | `#26C4D2EF` | About 15% alpha; fine stroke or light tint |
| Stronger tint | `#33C4D2EF` | About 20% alpha; localized highlight |
| Press overlay | `#14FFFFFF` | About 8% white overlay |
| Subtle white overlay | `#19FFFFFF` | About 10% white overlay |

Alpha is encoded in the first byte of Android ARGB. For a translucent overlay, composite the cool tint over the opaque surface rather than lowering the opacity of text or the whole card.

| Metric | Value |
|---|---:|
| Spacing seen in primary screens | 8, 16, 24 dp |
| Small card corner | 8 dp |
| Search field corner | 32 dp / pill |
| Sheet top corner | 16 dp |
| Standard outline | 1 dp |
| Selected-plan outline | 3 dp |
| Acrylic shadow | 20 dp blur radius, 16% black, `(x=0, y=2)` dp |
| Default backdrop blur | **0 dp** |
| Contrast gradient (optional overlay) | `#88000000 → #00000000` |
| Body type | IBM Plex Sans, regular/medium/bold |
| Display/title type | Metropolis, medium/semibold/bold |

Use the provided system font fallback if bundled fonts are unavailable. Keep body copy regular and reserve bold for short labels and selected filters.

## 3. Acrylic / glass recipe

**Faithful recipe:** `opaque dark surface + 1 dp cool translucent border + soft black shadow`; optional localized tint at 15–20% alpha. Use **0 dp backdrop blur** for the baseline. This matches the inspected acrylic surface configuration and keeps text and icon previews crisp.

The 20 dp shadow blur is separate from backdrop blur. If a product requirement later adds live backdrop blur, apply it to a true backdrop-capture layer on API 31+ and calibrate it visually; a foreground `Modifier.blur` blurs the card’s own content, not the content behind it. The source does not establish a nonzero blur radius for its surface.

**API < 31 fallback:** render the same solid surface, translucent edge, optional low-alpha tint, and shadow; omit live blur. This is also the default visual treatment, so the fallback does not look like a degraded screen. Keep ambient animated highlights optional and static/absent on unsupported or non-hardware-accelerated devices.

The baseline acrylic panel has no gradient fill. If image contrast needs help, apply a separate black-to-transparent overlay (`#88000000 → #00000000`) over the image, not inside every card.

## 4. Components and Compose examples

### Component measurements and states

| Component | Size / shape | Normal | Pressed / selected | Disabled |
|---|---|---|---|---|
| Icon tile | 132 dp tall; 72 dp preview; 12 sp one-line centered label; 8 dp corners | Dark surface, 1 dp outline | Surface changes one step lighter; 150 ms fade | Keep tile shape, reduce content opacity to 38%, no press animation |
| Primary/neutral action | 36 dp visual height, 88 dp minimum width, 16 dp horizontal padding, 14 sp bold label | Blue fill for primary; outlined/tinted neutral surface | Subtle 8% white overlay and scale response | 38% content opacity, no scale |
| Search field | 40 dp high; 24 dp horizontal screen margin; pill corners | 15% cool-blue tint, secondary hint | Raise tint/border slightly while focused | 38% content opacity |
| Filter chip | In a 48 dp horizontal row; 8 dp item gap; 24 dp horizontal inset | Compact single-line label | Selected label is stronger/bold; keep the pill restrained | Lower text opacity; no state animation |
| Toggle | 36 × 22 dp visual track; at least 48 dp touch target | Dark track, light thumb | Blue-tinted track and animated thumb position | Keep geometry, reduce contrast; ignore taps |
| Slider | Fill available width; reserve a 48 dp interaction height | Low-contrast track with blue active portion | Blue active portion/thumb follows drag | Lower track/thumb contrast and disable drag |
| Checkbox | 20 dp visual box; 2 dp outline | Transparent/dark fill | Draw check/reveal with a spring | Reduced contrast; no check motion |
| Bottom app bar | 64 dp high; 16 dp horizontal inset; three balanced slots | Background surface with 1 dp divider | Selected destination uses stronger blue/white icon and label | Disabled action stays in place at reduced opacity |
| Collapsible app bar | 56 dp baseline height | Title and actions on dark surface | Collapse/expand with scroll; fade/settle on release | Not applicable |
| Bottom sheet | Content-height; 16 dp top inset, 16 dp top corners | Solid raised surface | Drag between hidden/expanded; dim underlying content only as needed | Not applicable |

The primary screenshot data establishes a 132 dp card height but not a fixed number of grid columns. Choose columns from available width and preserve the tile’s preview/label proportions.

### Reusable panel, tile and button

```kotlin
private val SurfaceColor = Color(0xFF13161B)
private val PressedSurface = Color(0xFF292D35)
private val ActionBlue = Color(0xFF006ED5)
private val EdgeTint = Color(0x26C4D2EF)
private val PressTint = Color(0x14FFFFFF)

@Composable
fun AcrylicPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .shadow(2.dp, shape)
            .clip(shape)
            .background(SurfaceColor)
            .border(1.dp, EdgeTint, shape)
            .padding(12.dp),
        content = content,
    )
}

@Composable
fun IconTile(
    title: String,
    preview: @Composable BoxScope.() -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 1400f),
        label = "tile-press",
    )
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .height(132.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (pressed && enabled) PressedSurface else SurfaceColor)
            .border(1.dp, EdgeTint, shape)
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = null,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.38f)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(72.dp), content = preview)
        Text(
            text = title,
            maxLines = 1,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 1400f),
        label = "button-press",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier
            .height(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ActionBlue,
            contentColor = Color.White,
            disabledContainerColor = ActionBlue.copy(alpha = 0.38f),
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
```

`shadow(2.dp)` is a portable Compose approximation; retain the listed 20 dp soft-shadow token when implementing a custom shadow renderer. Do not substitute that shadow value for the backdrop-blur radius.

### Search and switch

```kotlin
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(EdgeTint)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) {
                    Text("Search", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
                }
                field()
            }
        },
    )
}

@Composable
fun AcrylicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 520f),
        label = "switch-position",
    )
    val track = lerp(SurfaceColor, ActionBlue, progress)
        .copy(alpha = if (enabled) 1f else 0.38f)

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 36.dp, height = 22.dp)) {
            val r = size.height / 2f
            drawRoundRect(track, cornerRadius = CornerRadius(r, r))
            drawRoundRect(
                EdgeTint,
                cornerRadius = CornerRadius(r, r),
                style = Stroke(1.dp.toPx()),
            )
            drawCircle(
                color = Color.White,
                radius = r - 2.dp.toPx(),
                center = Offset(r + (size.width - 2f * r) * progress, r),
            )
        }
    }
}

@Composable
fun AcrylicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        valueRange = 0f..1f,
        modifier = modifier.heightIn(min = 48.dp),
        colors = SliderDefaults.colors(
            thumbColor = ActionBlue,
            activeTrackColor = ActionBlue,
            inactiveTrackColor = Color.White.copy(alpha = 0.20f),
            disabledThumbColor = ActionBlue.copy(alpha = 0.38f),
            disabledActiveTrackColor = ActionBlue.copy(alpha = 0.38f),
            disabledInactiveTrackColor = Color.White.copy(alpha = 0.10f),
        ),
    )
}
```

The source does not establish a slider track/thumb measurement; the 48 dp height above is an accessible interaction target, and the Material slider is a Compose starting point rather than a claim about exact original geometry. Use a 20 dp checkbox visual with a 2 dp edge and a short spring reveal; use a 48 dp minimum interaction area around any smaller visual. Keep filter chips in a horizontally scrollable row. For icon tiles, use a preview image/illustration supplied by the new product, not an imitation of the source app’s icon or branding.

## 5. Motion

| Interaction | Compose target |
|---|---|
| Press and release on a card/button | Scale `1.00 → ~0.98`; spring damping `0.9`, stiffness `1400`. On release, use a 70 ms settle stage and spring damping `0.42`, stiffness `420`; cancel returns with damping `1.0`, stiffness `600`. |
| Switch changes | Animate progress `0 ↔ 1` with spring damping `0.58`, stiffness `520`; thumb stretch may use press spring `(0.9, 1400)` and return `(1.0, 600)`. |
| Checkbox toggles | Animate check/reveal with spring stiffness `600`; a secondary reveal can use stiffness `900` after a 60 ms delay. |
| Screen changes | Fade in/out over `150 ms` linear. Where horizontal movement is desired, translate one screen width over `200 ms` with a decelerating curve. |
| Decorative light | Optional API 31+ ambient blobs; animate phase over `6,000 ms` per segment with cubic Bezier `(0.42, 0, 0.58, 1)`. Remove or freeze the effect when hardware rendering is unavailable. |

```kotlin
@Composable
fun rememberPressScale(pressed: Boolean): Float =
    animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 1400f),
        label = "press-scale",
    ).value

val pageEnter = fadeIn(tween(150, easing = LinearEasing))
val pageExit = fadeOut(tween(150, easing = LinearEasing))
```

Respect system animator scale and reduced-motion preferences. Do not animate large background areas continuously unless the decorative effect is visible and low contrast.

## 6. Haptic and sound

No app-specific haptic or sound effect is part of the observed interaction language. Keep standard system feedback enabled where appropriate, but do not add custom click sounds or strong vibration to routine selection, toggle, or navigation actions.

## 7. Layout rules

- Use a dark full-screen canvas and edge-to-edge system bars; keep content clear of status/navigation insets.
- Keep the main screen hierarchy stable: 56 dp top bar, controls/search, scrollable content, persistent 64 dp bottom navigation.
- Use 24 dp horizontal insets for search/filter content, 16 dp for the bottom bar, and 8 dp between adjacent compact controls.
- Use adaptive columns; keep icon artwork centered in a 72 dp preview region and its label on one line.
- Use 40 dp search fields and 48 dp minimum touch targets, even when the visible control is smaller.
- Keep bottom sheets content-sized with a 16 dp top radius. Separate sections with spacing and a fine border instead of nested heavy shadows.
- Preserve high contrast: reserve low-alpha blue/white for surfaces, outlines, hint text, and ambient light—not body text.

## 8. Do / Don’t

1. **Do** start with `#000000` canvas and near-black surfaces; **don’t** make every panel a bright translucent overlay.
2. **Do** use a cool-blue 1 dp edge; **don’t** use thick glowing borders on every card.
3. **Do** keep actual baseline backdrop blur at `0 dp`; **don’t** call a shadow or foreground blur “glass blur.”
4. **Do** use low-alpha color locally; **don’t** lower opacity for labels, icons, and the whole component together.
5. **Do** use 8/16/24 dp spacing and 8/16/32 dp corner families; **don’t** mix arbitrary radii within one screen.
6. **Do** use brief fades and restrained spring press feedback; **don’t** bounce full cards or animate every list item.
7. **Do** support API < 31 with a complete tinted-surface fallback; **don’t** make blur required for legibility.
8. **Do** preserve a 48 dp touch target and readable contrast; **don’t** shrink the hit area to the 20–40 dp visual.
9. **Do** use the new app’s own iconography and product language; **don’t** copy source logos, exact assets, or branded illustrations.

## 9. Prompt to give another AI

> Create a dark icon-customization Android screen in Kotlin + Jetpack Compose using this design system: pure-black canvas; near-black `#13161B` surfaces; white primary text; blue actions around `#006ED5` / `#317AFF`; cool translucent edge `#26C4D2EF`; 1 dp outlines; 8 dp cards; pill search; 8/16/24 dp spacing; IBM Plex Sans-like body and a geometric semibold display face. Keep the acrylic effect mostly solid/tinted with a soft shadow and **0 dp required backdrop blur**; below API 31 use the same surface treatment. Use a 56 dp top bar, 40 dp search, horizontally scrolling filters, adaptive icon grid with 132 dp tiles and 72 dp previews, and a 64 dp bottom bar. Animate presses with a subtle spring and page changes with a 150 ms fade. Maintain contrast and 48 dp hit targets. Create original icons, copy, and branding; do not imitate any existing app’s logo or exact assets.
