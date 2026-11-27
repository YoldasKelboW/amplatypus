// SPDX-License-Identifier: GPL-3.0-or-later
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Threading.Tasks;
using Windows.Storage;
using Windows.Storage.AccessCache;
using Windows.Storage.Pickers;
using Windows.Storage.Search;
using Windows.System;
using Windows.UI.Core;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Input;
using Windows.UI.Xaml.Navigation;

namespace Amplatypus.Xbox
{
    /// <summary>One module file on the Xbox.</summary>
    public sealed class ModuleEntry
    {
        public ModuleEntry(StorageFile file, string folder)
        {
            File = file;
            Folder = folder;
        }

        public StorageFile File { get; }
        public string Name => File.Name;
        public string Folder { get; }
    }

    /// <summary>
    /// Modland (searched on the device, played from its mirrors) and the modules on the Xbox, and the one
    /// playing. The controller moves between the lists and the buttons; A plays the module under the focus.
    /// </summary>
    public sealed partial class MainPage : Page
    {
        private const string FolderToken = "modules";
        private readonly ObservableCollection<object> modlandItems = new ObservableCollection<object>();
        private readonly ObservableCollection<object> localItems = new ObservableCollection<object>();
        private readonly ModulePlayer player = new ModulePlayer();
        private readonly DispatcherTimer clock = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(250) };
        private ModlandCatalog modland;
        private string[] extensions = new string[0];
        // The list the playing module came from, so that Next goes on in it.
        private ObservableCollection<object> playingList;
        private int current = -1;
        private int loading;
        private string playingType = string.Empty;
        private string playingTitle = string.Empty;

        public MainPage()
        {
            InitializeComponent();
            ModlandList.ItemsSource = modlandItems;
            LocalList.ItemsSource = localItems;
            player.Ended += (sender, args) => _ = Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () => PlayAt(playingList, current + 1));
            clock.Tick += (sender, args) => ShowProgress();
            Tracker.Closed += (sender, args) => TrackerButton.Focus(FocusState.Programmatic);
        }

        protected override async void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            if (!await player.StartAsync())
            {
                Status.Text = "No sound output available.";
                return;
            }
            clock.Start();
            extensions = Core.TrackerExtensions().Concat(Core.SupportedExtensions()).Distinct().ToArray();
            await LoadLocalAsync();
            await LoadModlandAsync();
        }

        // ---- Modland ----

        private async Task LoadModlandAsync()
        {
            modland = new ModlandCatalog(Core.TrackerExtensions(), Core.SupportedExtensions());
            try
            {
                await modland.LoadAsync(text => _ = Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () => Status.Text = text));
                Status.Text = modland.Count.ToString("N0") + " modules on Modland";
                Query.IsEnabled = SearchButton.IsEnabled = RandomButton.IsEnabled = true;
                ShowModland(modland.Random());
            }
            catch (Exception)
            {
                Status.Text = "Modland cannot be reached. Modules on this Xbox still play.";
            }
        }

        private void ShowModland(IEnumerable<ModlandEntry> entries)
        {
            modlandItems.Clear();
            foreach (var entry in entries) modlandItems.Add(entry);
        }

        private void OnSearch(object sender, RoutedEventArgs e) => Search();

        private void OnQueryKeyDown(object sender, KeyRoutedEventArgs e)
        {
            if (e.Key == VirtualKey.Enter || e.Key == VirtualKey.GamepadA)
            {
                e.Handled = true;
                Search();
            }
        }

        private void Search()
        {
            if (modland == null) return;
            var found = modland.Search(Query.Text);
            ShowModland(found);
            Status.Text = found.Count == 0 ? "Nothing found." : found.Count + (found.Count >= 300 ? "+" : "") + " found";
            if (found.Count > 0) ModlandList.Focus(FocusState.Programmatic);
        }

        private void OnRandom(object sender, RoutedEventArgs e)
        {
            if (modland == null) return;
            ShowModland(modland.Random());
            PlayAt(modlandItems, 0);
        }

        // ---- This Xbox ----

        private async Task LoadLocalAsync()
        {
            localItems.Clear();
            var types = extensions.Select(ext => "." + ext).ToList();
            var folders = new List<StorageFolder>();
            try
            {
                folders.Add(KnownFolders.MusicLibrary);
            }
            catch (Exception)
            {
                // No music library on this device.
            }
            if (StorageApplicationPermissions.FutureAccessList.ContainsItem(FolderToken))
            {
                try
                {
                    folders.Add(await StorageApplicationPermissions.FutureAccessList.GetFolderAsync(FolderToken));
                }
                catch (Exception)
                {
                    StorageApplicationPermissions.FutureAccessList.Remove(FolderToken);
                }
            }
            foreach (var folder in folders)
            {
                try
                {
                    var query = folder.CreateFileQueryWithOptions(new QueryOptions(CommonFileQuery.OrderByName, types));
                    foreach (var file in await query.GetFilesAsync()) localItems.Add(new ModuleEntry(file, folder.DisplayName));
                }
                catch (Exception)
                {
                    // A folder that cannot be searched is skipped.
                }
            }
        }

        private async void OnChooseFolder(object sender, RoutedEventArgs e)
        {
            var picker = new FolderPicker { SuggestedStartLocation = PickerLocationId.MusicLibrary };
            picker.FileTypeFilter.Add("*");
            StorageFolder folder = await picker.PickSingleFolderAsync();
            if (folder == null) return;
            StorageApplicationPermissions.FutureAccessList.AddOrReplace(FolderToken, folder);
            await LoadLocalAsync();
        }

        // ---- Playing ----

        private void OnItemClick(object sender, ItemClickEventArgs e)
        {
            var list = sender == ModlandList ? modlandItems : localItems;
            PlayAt(list, list.IndexOf(e.ClickedItem));
        }

        private async void PlayAt(ObservableCollection<object> list, int index)
        {
            if (list == null) return;
            int request = ++loading;
            // Modules that cannot be fetched or read are passed over, up to the end of the list.
            for (; index >= 0 && index < list.Count; index++)
            {
                object item = list[index];
                Title.Text = "Loading…";
                byte[] data = await ReadAsync(item);
                // Another module was asked for in the meantime.
                if (request != loading) return;
                string fileName = item is ModlandEntry named ? named.Path.Substring(named.Path.LastIndexOf('/') + 1) : ((ModuleEntry)item).Name;
                if (data != null && player.Play(data, fileName, out string title, out string type))
                {
                    playingList = list;
                    current = index;
                    (list == modlandItems ? ModlandList : LocalList).SelectedIndex = index;
                    var entry = item as ModlandEntry;
                    Title.Text = !string.IsNullOrWhiteSpace(title) ? title : entry?.Title ?? ((ModuleEntry)item).Name;
                    Composer.Text = entry?.Composer ?? string.Empty;
                    Format.Text = type;
                    playingType = type;
                    playingTitle = Title.Text;
                    PlayButton.Content = "Pause";
                    return;
                }
            }
            Title.Text = "Nothing playing";
            Composer.Text = Format.Text = string.Empty;
        }

        private async Task<byte[]> ReadAsync(object item)
        {
            try
            {
                if (item is ModlandEntry entry) return await modland.DownloadAsync(entry);
                var buffer = await FileIO.ReadBufferAsync(((ModuleEntry)item).File);
                return buffer.ToArray();
            }
            catch (Exception)
            {
                return null;
            }
        }

        private void OnPrevious(object sender, RoutedEventArgs e)
        {
            // Back to the start of the module, or to the one before when it has only just begun.
            if (player.Position > 3 || current <= 0) player.Seek(0);
            else PlayAt(playingList, current - 1);
        }

        private void OnNext(object sender, RoutedEventArgs e) => PlayAt(playingList ?? modlandItems, current + 1);

        private void OnPlayPause(object sender, RoutedEventArgs e)
        {
            if (current < 0)
            {
                var list = Sources.SelectedIndex == 0 ? modlandItems : localItems;
                var view = Sources.SelectedIndex == 0 ? ModlandList : LocalList;
                PlayAt(list, Math.Max(0, view.SelectedIndex));
                return;
            }
            player.TogglePause();
            PlayButton.Content = player.IsPlaying ? "Pause" : "Play";
        }

        // ---- Tracker view ----

        private void OnTrackerView(object sender, RoutedEventArgs e) => OpenTracker();

        private void OnPageKeyDown(object sender, KeyRoutedEventArgs e)
        {
            if (e.Key == VirtualKey.GamepadY && Tracker.Visibility != Visibility.Visible)
            {
                e.Handled = true;
                OpenTracker();
            }
        }

        private void OpenTracker()
        {
            if (current < 0) return;
            Tracker.Show(player, playingType, playingTitle);
        }

        private void ShowProgress()
        {
            double duration = player.Duration, position = player.Position;
            Progress.Value = duration > 0 ? Math.Min(1, position / duration) : 0;
            Time.Text = current < 0 ? string.Empty : ClockText(position) + " / " + ClockText(duration);
        }

        private static string ClockText(double seconds)
        {
            var time = TimeSpan.FromSeconds(Math.Max(0, seconds));
            return (int)time.TotalMinutes + ":" + time.Seconds.ToString("00");
        }
    }
}
