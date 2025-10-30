import { bootstrapApplication } from '@angular/platform-browser';
import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  standalone: true,
  template: `<h1>Scala + Angular Template</h1><p>Angular dev proxy will forward /api to backend.</p>`
})
class AppComponent {}

bootstrapApplication(AppComponent).catch(err => console.error(err));
