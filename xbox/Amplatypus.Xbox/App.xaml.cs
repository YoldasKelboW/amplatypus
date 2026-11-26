// SPDX-License-Identifier: GPL-3.0-or-later
using Windows.ApplicationModel.Activation;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;

namespace Amplatypus.Xbox
{
    sealed partial class App : Application
    {
        public App()
        {
            InitializeComponent();
            // On Xbox the controller moves the focus; the mouse-like pointer only appears where asked for.
            RequiresPointerMode = ApplicationRequiresPointerMode.WhenRequested;
        }

        protected override void OnLaunched(LaunchActivatedEventArgs e)
        {
            if (!(Window.Current.Content is Frame frame))
            {
                frame = new Frame();
                Window.Current.Content = frame;
            }
            if (frame.Content == null) frame.Navigate(typeof(MainPage), e.Arguments);
            Window.Current.Activate();
        }
    }
}
