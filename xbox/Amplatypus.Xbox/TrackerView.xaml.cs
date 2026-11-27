// SPDX-License-Identifier: GPL-3.0-or-later
using System;
using System.Collections.Generic;
using System.Linq;
using Windows.System;
using Windows.UI;
using Windows.UI.Text;
using Windows.Foundation;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Documents;
using Windows.UI.Xaml.Input;
using Windows.UI.Xaml.Media;
using Windows.UI.Xaml.Shapes;

namespace Amplatypus.Xbox
{
    /// <summary>The colours and words of one tracker's look; the values are the Android app's.</summary>
    internal sealed class TrackerTheme
    {
        public Color Face, Light, Shadow, Label, Value, ValueBox, Screen, Pattern, PatternDim, RowBar, RowText, Level;
        public string[] Readouts;
        public bool DecimalRows = true;

        private static Color C(uint argb) => Color.FromArgb((byte)(argb >> 24), (byte)(argb >> 16), (byte)(argb >> 8), (byte)argb);

        // ProTracker: grey panels, blue notes on black.
        public static readonly TrackerTheme ProTracker = new TrackerTheme
        {
            Face = C(0xFFAAAAAA), Light = C(0xFFDDDDDD), Shadow = C(0xFF555555), Label = C(0xFF000000), Value = C(0xFF000000),
            ValueBox = C(0xFFBBBBBB), Screen = C(0xFF000000), Pattern = C(0xFF4455EE), PatternDim = C(0xFF2A3399),
            RowBar = C(0xFFBBBBBB), RowText = C(0xFF000000), Level = C(0xFF22DD22),
            Readouts = new[] { "POS", "PATTERN", "LENGTH", "BPM", "SPEED" }, DecimalRows = false,
        };

        // FastTracker 2: blue-green panels, yellow pattern.
        public static readonly TrackerTheme FastTracker = new TrackerTheme
        {
            Face = C(0xFF46697A), Light = C(0xFF93B6C4), Shadow = C(0xFF1C2E36), Label = C(0xFFFFFFFF), Value = C(0xFFFFFFFF),
            ValueBox = C(0xFF000000), Screen = C(0xFF000000), Pattern = C(0xFFFFE36B), PatternDim = C(0xFFB3A04A),
            RowBar = C(0xFF3F7088), RowText = C(0xFFFFFFFF), Level = C(0xFFFFE36B),
            Readouts = new[] { "Songpos", "Pattern", "Length", "BPM", "Speed" }, DecimalRows = false,
        };

        // Impulse Tracker: khaki panels, green notes.
        public static readonly TrackerTheme Impulse = new TrackerTheme
        {
            Face = C(0xFFB69579), Light = C(0xFFEAEACA), Shadow = C(0xFF7D5945), Label = C(0xFF000000), Value = C(0xFFFFFF55),
            ValueBox = C(0xFF000000), Screen = C(0xFF000000), Pattern = C(0xFF66C46A), PatternDim = C(0xFF459948),
            RowBar = C(0xFF205500), RowText = C(0xFFEAEACA), Level = C(0xFFFFFF55),
            Readouts = new[] { "Order", "Pattern", "Length", "Tempo", "Speed" },
        };

        // Scream Tracker 3: gold panels.
        public static readonly TrackerTheme Scream = new TrackerTheme
        {
            Face = C(0xFFA69255), Light = C(0xFFFFDF85), Shadow = C(0xFF51452A), Label = C(0xFF000000), Value = C(0xFFFFDF85),
            ValueBox = C(0xFF000000), Screen = C(0xFF000000), Pattern = C(0xFFD4D0CC), PatternDim = C(0xFF9A9692),
            RowBar = C(0xFF183075), RowText = C(0xFFFFFFFF), Level = C(0xFF00B200),
            Readouts = new[] { "ORDER", "PATTERN", "LENGTH", "TEMPO", "SPEED" },
        };

        // HivelyTracker: steel-blue panels.
        public static readonly TrackerTheme Hively = new TrackerTheme
        {
            Face = C(0xFF6E8CA8), Light = C(0xFFA8C4E0), Shadow = C(0xFF203A54), Label = C(0xFFE0ECF8), Value = C(0xFFC0D4E8),
            ValueBox = C(0xFF000000), Screen = C(0xFF000000), Pattern = C(0xFFB8C8E0), PatternDim = C(0xFF8AA2BC),
            RowBar = C(0xFF607490), RowText = C(0xFFFFFFFF), Level = C(0xFFB8C8E0),
            Readouts = new[] { "Position", "Track", "Length", "Tempo", "Speed" },
        };

        // OctaMED: the blue, white and orange of the Amiga Workbench.
        public static readonly TrackerTheme OctaMed = new TrackerTheme
        {
            Face = C(0xFF0055AA), Light = C(0xFFFFFFFF), Shadow = C(0xFF000022), Label = C(0xFFFFFFFF), Value = C(0xFFFF8800),
            ValueBox = C(0xFF000022), Screen = C(0xFF002A55), Pattern = C(0xFFFFFFFF), PatternDim = C(0xFFA9C2E0),
            RowBar = C(0xFFFF8800), RowText = C(0xFF000022), Level = C(0xFFFF8800),
            Readouts = new[] { "Song", "Block", "Length", "Tempo", "Speed" },
        };

        /// <summary>The look that fits a module, by the format name its player reports.</summary>
        public static TrackerTheme For(string type)
        {
            string t = (type ?? string.Empty).ToLowerInvariant();
            if (t.Contains("fasttracker 2")) return FastTracker;
            if (t.Contains("impulse tracker") || t.Contains("openmpt")) return Impulse;
            if (t.Contains("scream tracker 3")) return Scream;
            if (t.Contains("ahx") || t.Contains("hively")) return Hively;
            if (t.Contains("octamed") || t.Contains("med ")) return OctaMed;
            return ProTracker;
        }
    }

    /// <summary>
    /// The tracker view over the player: B closes it, left and right move the channel cursor, A mutes the
    /// channel under it and X plays it alone.
    /// </summary>
    public sealed partial class TrackerView : UserControl
    {
        private const int VisibleLines = 21;
        private const double FontSize14 = 14;
        private const double LineHeight = 22;
        private static readonly FontFamily Pixel = new FontFamily("ms-appx:///Assets/Fonts/PressStart2P.ttf#Press Start 2P");

        private readonly DispatcherTimer timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(40) };
        private readonly TrackerState state = new TrackerState();
        private readonly List<TextBlock> lines = new List<TextBlock>();
        private readonly List<TextBlock> values = new List<TextBlock>();
        // One cell per channel shown: its frame (the cursor), level bar, waveform and number.
        private readonly List<Border> cells = new List<Border>();
        private readonly List<Rectangle> bars = new List<Rectangle>();
        private readonly List<Polyline> waves = new List<Polyline>();
        private readonly List<TextBlock> labels = new List<TextBlock>();
        private int cellsFirst = -1, cellsCount = -1;
        private int cursor;
        private ModulePlayer player;
        private TrackerTheme theme = TrackerTheme.ProTracker;
        private int firstChannel;
        private int visibleChannels = 4;

        /// <summary>Raised when the user leaves the view.</summary>
        public event EventHandler Closed;

        public TrackerView()
        {
            InitializeComponent();
            for (int i = 0; i < VisibleLines; i++)
            {
                var line = new TextBlock
                {
                    FontFamily = Pixel, FontSize = FontSize14, LineHeight = LineHeight, Height = LineHeight,
                    LineStackingStrategy = LineStackingStrategy.BlockLineHeight, TextWrapping = TextWrapping.NoWrap,
                };
                lines.Add(line);
                Lines.Children.Add(line);
            }
            RowBar.Height = LineHeight;
            RowBar.Margin = new Thickness(0, LineHeight * (VisibleLines / 2), 0, 0);
            timer.Tick += (sender, args) => Refresh();
            KeyDown += OnKeyDown;
        }

        internal void Show(ModulePlayer source, string type, string title)
        {
            player = source;
            theme = TrackerTheme.For(type);
            firstChannel = 0;
            cursor = 0;
            ApplyTheme();
            SongTitle.Text = title ?? string.Empty;
            Visibility = Visibility.Visible;
            timer.Start();
            Refresh();
            Focus(FocusState.Programmatic);
        }

        public void Hide()
        {
            timer.Stop();
            Visibility = Visibility.Collapsed;
            Closed?.Invoke(this, EventArgs.Empty);
        }

        private void ApplyTheme()
        {
            var face = new SolidColorBrush(theme.Face);
            Root.Background = new SolidColorBrush(theme.Shadow);
            foreach (var panel in new[] { TopPanel, LevelPanel })
            {
                panel.Background = face;
                panel.BorderBrush = new SolidColorBrush(theme.Light);
            }
            Screen.Background = new SolidColorBrush(theme.Screen);
            Screen.BorderBrush = new SolidColorBrush(theme.Shadow);
            RowBar.Fill = new SolidColorBrush(theme.RowBar);
            SongTitle.Foreground = new SolidColorBrush(theme.Label);
            SongTitle.FontFamily = Pixel;
            NoPattern.FontFamily = Pixel;
            NoPattern.Foreground = new SolidColorBrush(theme.Pattern);
            Hint.Foreground = new SolidColorBrush(theme.Light);
            Hint.FontFamily = Pixel;
            Readouts.Children.Clear();
            values.Clear();
            foreach (string name in theme.Readouts)
            {
                var value = new TextBlock { FontFamily = Pixel, FontSize = 14, Foreground = new SolidColorBrush(theme.Value), Text = "0" };
                values.Add(value);
                Readouts.Children.Add(new StackPanel
                {
                    Spacing = 4,
                    Children =
                    {
                        new TextBlock { FontFamily = Pixel, FontSize = 10, Foreground = new SolidColorBrush(theme.Label), Text = name },
                        new Border { Background = new SolidColorBrush(theme.ValueBox), Padding = new Thickness(6, 4, 6, 4), Child = value },
                    },
                });
            }
            ClearCells();
        }

        private void ClearCells()
        {
            cells.Clear();
            bars.Clear();
            waves.Clear();
            labels.Clear();
            Levels.Children.Clear();
            Levels.ColumnDefinitions.Clear();
            cellsFirst = cellsCount = -1;
        }

        private void Refresh()
        {
            if (player == null || !player.Fill(state)) return;
            values[0].Text = state.Order.ToString(theme.DecimalRows ? "000" : "X2");
            values[1].Text = state.Pattern.ToString(theme.DecimalRows ? "000" : "X2");
            values[2].Text = state.Orders.ToString(theme.DecimalRows ? "000" : "X2");
            values[3].Text = state.Tempo.ToString();
            values[4].Text = state.Speed.ToString();
            int channels = state.Levels.Length;
            cursor = Math.Max(0, Math.Min(cursor, channels - 1));
            RefreshPattern();
            RefreshChannels();
        }

        // The scopes and level bars of the channels in the pattern window, or of all for formats without one.
        private void RefreshChannels()
        {
            int channels = state.Levels.Length;
            bool hasPattern = state.Rows.Length > 0;
            int first = hasPattern ? firstChannel : 0;
            int count = hasPattern ? Math.Min(visibleChannels, Math.Max(0, channels - first)) : channels;
            if (first != cellsFirst || count != cellsCount)
            {
                ClearCells();
                cellsFirst = first;
                cellsCount = count;
                for (int i = 0; i < count; i++)
                {
                    Levels.ColumnDefinitions.Add(new ColumnDefinition());
                    var bar = new Rectangle { Fill = new SolidColorBrush(theme.Level), Opacity = 0.35, VerticalAlignment = VerticalAlignment.Bottom };
                    var wave = new Polyline { Stroke = new SolidColorBrush(theme.Pattern), StrokeThickness = 2 };
                    var label = new TextBlock
                    {
                        FontFamily = Pixel, FontSize = 10, Foreground = new SolidColorBrush(theme.Pattern), Margin = new Thickness(4),
                        HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top,
                    };
                    var cell = new Border
                    {
                        Background = new SolidColorBrush(theme.Screen), BorderThickness = new Thickness(2), Margin = new Thickness(2, 0, 2, 0),
                        Child = new Grid { Children = { bar, wave, label } },
                    };
                    Grid.SetColumn(cell, i);
                    cells.Add(cell);
                    bars.Add(bar);
                    waves.Add(wave);
                    labels.Add(label);
                    Levels.Children.Add(cell);
                }
            }
            var cursorBrush = new SolidColorBrush(theme.Light);
            var plainBrush = new SolidColorBrush(theme.Shadow);
            int points = TrackerState.ScopePoints;
            bool hasScopes = state.Scopes.Length >= channels * points;
            for (int i = 0; i < count; i++)
            {
                int channel = first + i;
                bool muted = channel < state.Muted.Length && state.Muted[channel];
                cells[i].BorderBrush = channel == cursor ? cursorBrush : plainBrush;
                cells[i].Opacity = muted ? 0.4 : 1.0;
                labels[i].Text = (channel + 1).ToString() + (muted ? " M" : string.Empty);
                double width = Math.Max(0, cells[i].ActualWidth - 4), height = Math.Max(0, cells[i].ActualHeight - 4);
                bars[i].Height = Math.Min(1.0, Math.Max(0.0, state.Levels[channel])) * height;
                var line = new PointCollection();
                if (hasScopes && width > 0)
                {
                    double middle = height / 2;
                    for (int x = 0; x < points; x++)
                    {
                        double value = Math.Max(-1.0, Math.Min(1.0, state.Scopes[channel * points + x] * 1.5));
                        line.Add(new Point(x * width / (points - 1), middle - value * middle * 0.9));
                    }
                }
                waves[i].Points = line;
            }
        }

        private void RefreshPattern()
        {
            bool hasPattern = state.Rows.Length > 0;
            NoPattern.Visibility = hasPattern ? Visibility.Collapsed : Visibility.Visible;
            RowBar.Visibility = hasPattern ? Visibility.Visible : Visibility.Collapsed;
            if (!hasPattern)
            {
                foreach (var line in lines) line.Inlines.Clear();
                return;
            }
            int channels = state.Rows[0].Length;
            int cellLength = Math.Max(3, state.Rows[0].Length > 0 ? state.Rows[0][0].Length : 3);
            // Each character of the pixel font is as wide as the font is high.
            double width = Math.Max(0, Screen.ActualWidth - 24);
            visibleChannels = Math.Max(1, (int)((width / FontSize14 - 4) / (cellLength + 1)));
            firstChannel = Math.Max(0, Math.Min(firstChannel, Math.Max(0, channels - visibleChannels)));
            var pattern = new SolidColorBrush(theme.Pattern);
            var dim = new SolidColorBrush(theme.PatternDim);
            var current = new SolidColorBrush(theme.RowText);
            for (int i = 0; i < VisibleLines; i++)
            {
                int row = state.Row + i - VisibleLines / 2;
                var line = lines[i];
                if (row < 0 || row >= state.Rows.Length)
                {
                    line.Inlines.Clear();
                    continue;
                }
                string number = theme.DecimalRows ? row.ToString("00") : row.ToString("X2");
                var brush = i == VisibleLines / 2 ? current : (row % 4 == 0 ? pattern : dim);
                var faint = new SolidColorBrush(Color.FromArgb(0x50, brush.Color.R, brush.Color.G, brush.Color.B));
                line.Inlines.Clear();
                line.Inlines.Add(new Run { Text = number + " ", Foreground = brush });
                string[] cellsOfRow = state.Rows[row];
                int last = Math.Min(cellsOfRow.Length, firstChannel + visibleChannels);
                for (int channel = firstChannel; channel < last; channel++)
                {
                    bool muted = channel < state.Muted.Length && state.Muted[channel];
                    if (channel > firstChannel) line.Inlines.Add(new Run { Text = "|", Foreground = brush });
                    line.Inlines.Add(new Run { Text = cellsOfRow[channel], Foreground = muted ? faint : brush });
                }
            }
        }

        // Moves the cursor a channel along and the pattern window with it.
        private void MoveCursor(int delta)
        {
            int channels = state.Levels.Length;
            if (channels == 0) return;
            cursor = Math.Max(0, Math.Min(channels - 1, cursor + delta));
            if (cursor < firstChannel) firstChannel = cursor;
            if (cursor >= firstChannel + visibleChannels) firstChannel = cursor - visibleChannels + 1;
            Refresh();
        }

        private void OnKeyDown(object sender, KeyRoutedEventArgs e)
        {
            switch (e.Key)
            {
                case VirtualKey.GamepadB:
                case VirtualKey.Escape:
                case VirtualKey.Back:
                    e.Handled = true;
                    Hide();
                    break;
                case VirtualKey.GamepadDPadLeft:
                case VirtualKey.GamepadLeftThumbstickLeft:
                case VirtualKey.Left:
                    e.Handled = true;
                    MoveCursor(-1);
                    break;
                case VirtualKey.GamepadDPadRight:
                case VirtualKey.GamepadLeftThumbstickRight:
                case VirtualKey.Right:
                    e.Handled = true;
                    MoveCursor(1);
                    break;
                case VirtualKey.GamepadA:
                case VirtualKey.Enter:
                case VirtualKey.Space:
                case VirtualKey.M:
                    e.Handled = true;
                    player?.ToggleMute(cursor);
                    Refresh();
                    break;
                case VirtualKey.GamepadX:
                case VirtualKey.S:
                    e.Handled = true;
                    player?.Solo(cursor);
                    Refresh();
                    break;
            }
        }
    }
}
