import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import {EventScannerService} from '../../shared/services/EventScannerService';

@Component({
    selector: 'app-dashboard',
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.scss'],
    animations: [routerTransition()]
})
export class DashboardComponent implements OnInit {
    public alerts: Array<any> = [];
    public sliders: Array<any> = [];
    private events: any;
    private users: any;
    private projects: any;


    constructor(public eventScannerService:EventScannerService) {
        this.sliders.push(
            {
                imagePath: 'assets/images/slider1.png',
                label: 'First slide label',
                text:
                    'Nulla vitae elit libero, a pharetra augue mollis interdum.'
            },
            {
                imagePath: 'assets/images/slider1.png',
                label: 'Second slide label',
                text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit.'
            },
            {
                imagePath: 'assets/images/slider1.png',
                label: 'Third slide label',
                text:
                    'Praesent commodo cursus magna, vel scelerisque nisl consectetur.'
            }
        );

        this.alerts.push(
            {
                id: 1,
                type: 'success',
                message: `Lorem ipsum dolor sit amet, consectetur adipisicing elit.
                Voluptates est animi quibusdam praesentium quam, et perspiciatis,
                consectetur velit culpa molestias dignissimos
                voluptatum veritatis quod aliquam! Rerum placeat necessitatibus, vitae dolorum`
            },
            {
                id: 2,
                type: 'warning',
                message: `Lorem ipsum dolor sit amet, consectetur adipisicing elit.
                Voluptates est animi quibusdam praesentium quam, et perspiciatis,
                consectetur velit culpa molestias dignissimos
                voluptatum veritatis quod aliquam! Rerum placeat necessitatibus, vitae dolorum`
            }
        );

        this.eventScannerService.getEvents().subscribe(events =>{
            this.events = events;
        });
        this.eventScannerService.getUsers().subscribe(users =>{
            this.users = users;
        });
        this.eventScannerService.getProjects().subscribe(projects =>{
            this.projects = projects;
        });
        setInterval(() =>{
            this.eventScannerService.getEvents().subscribe(events =>{
                this.events = events;
            });
        },3000);
    }

    ngOnInit() {}

    public closeAlert(alert: any) {
        const index: number = this.alerts.indexOf(alert);
        this.alerts.splice(index, 1);
    }


    getName(sid){
        let returnData;
        this.users.data.forEach(item =>{
            if(item.sid == sid)
                returnData= item.name;
        })
        return returnData;
    }

    getProject(code){
        let returnData;
        this.projects.data.forEach(item =>{
            if(item.barCode == code)
                returnData= item.name;
        })
        return returnData;

    }

    clearEvents(){
        this.eventScannerService.clearEvents().subscribe(projects =>{
            alert("Events cleared successfully!");
        });
    }
}
