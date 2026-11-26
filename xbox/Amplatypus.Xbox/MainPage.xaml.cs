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
using Windows.UI.Core;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Navigation;

namespace Amplatypus.Xbox
{
    /// <summary>One module file in the list.</summary>
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
    /// The modules in the music library and a folder the user picks, and the one playing. The controller
    /// moves between the list and the buttons; A plays the module under the focus.
    /// </summary>
    public sealed partial class MainPage : Page
    {
        private const string FolderToken = "modules";
        private readonly ObservableCollection<ModuleEntry> modules = new ObservableCollection<ModuleEntry>();
        private readonly ModulePlayer player = new ModulePlayer();
        private readonly DispatcherTimer clock = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(250) };
        private int current = -1;

        public MainPage()
        {
            InitializeComponent();
            Modules.ItemsSource = modules;
            player.Ended += (sender, args) => _ = Dispatcher.RunAsync(CoreDispatcherPriority.Normal, () => PlayAt(current + 1));
            clock.Tick += (sender, args) => ShowProgress();
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
            await LoadModulesAsync();
        }

        private async Task LoadModulesAsync()
        {
            Status.Text = "Looking for modules…";
            modules.Clear();
            var types = OpenMpt.SupportedExtensions().Select(ext => "." + ext).ToList();
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
                    foreach (var file in await query.GetFilesAsync())
                    {
                        modules.Add(new ModuleEntry(file, folder.DisplayName));
                    }
                }
                catch (Exception)
                {
                    // A folder that cannot be searched is skipped.
                }
            }
            Status.Text = modules.Count == 0 ? "No modules found. Put some in Music, or choose a folder." : modules.Count + " modules";
            if (modules.Count > 0) Modules.Focus(FocusState.Programmatic);
        }

        private async void OnChooseFolder(object sender, RoutedEventArgs e)
        {
            var picker = new FolderPicker { SuggestedStartLocation = PickerLocationId.MusicLibrary };
            picker.FileTypeFilter.Add("*");
            StorageFolder folder = await picker.PickSingleFolderAsync();
            if (folder == null) return;
            StorageApplicationPermissions.FutureAccessList.AddOrReplace(FolderToken, folder);
            await LoadModulesAsync();
        }

        private void OnModuleClick(object sender, ItemClickEventArgs e)
        {
            PlayAt(modules.IndexOf((ModuleEntry)e.ClickedItem));
        }

        private async void PlayAt(int index)
        {
            // Modules libopenmpt cannot read are passed over, up to the end of the list.
            for (; index >= 0 && index < modules.Count; index++)
            {
                LoadedFile loaded = await ReadAsync(modules[index].File);
                if (loaded.Bytes != null && player.Play(loaded.Bytes, out string title, out string type))
                {
                    current = index;
                    Modules.SelectedIndex = index;
                    Title.Text = string.IsNullOrWhiteSpace(title) ? modules[index].Name : title;
                    Format.Text = type;
                    PlayButton.Content = "Pause";
                    return;
                }
            }
            Title.Text = "Nothing playing";
            Format.Text = string.Empty;
        }

        private struct LoadedFile
        {
            public byte[] Bytes;
        }

        private static async Task<LoadedFile> ReadAsync(StorageFile file)
        {
            try
            {
                var buffer = await FileIO.ReadBufferAsync(file);
                return new LoadedFile { Bytes = buffer.ToArray() };
            }
            catch (Exception)
            {
                return new LoadedFile();
            }
        }

        private void OnPrevious(object sender, RoutedEventArgs e)
        {
            // Back to the start of the module, or to the one before when it has only just begun.
            if (player.Position > 3 || current <= 0) player.Seek(0);
            else PlayAt(current - 1);
        }

        private void OnNext(object sender, RoutedEventArgs e) => PlayAt(current + 1);

        private void OnPlayPause(object sender, RoutedEventArgs e)
        {
            if (current < 0)
            {
                PlayAt(Math.Max(0, Modules.SelectedIndex));
                return;
            }
            player.TogglePause();
            PlayButton.Content = player.IsPlaying ? "Pause" : "Play";
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
