import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { TimelineRoutingModule } from './Timeline-routing.module';
import { TimelineComponent } from './Timeline.component';
import { PageHeaderModule } from '../../shared';

@NgModule({
    imports: [CommonModule,  TimelineRoutingModule, PageHeaderModule],
    declarations: [TimelineComponent]
})
export class TimelineModule {}
